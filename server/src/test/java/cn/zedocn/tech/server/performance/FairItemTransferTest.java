package cn.zedocn.tech.server.performance;

import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FairItemTransferTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        for (var item : List.of(Items.DIAMOND, Items.EMERALD)) {
            if (!item.builtInRegistryHolder().areComponentsBound()) item.builtInRegistryHolder().bindComponents(components);
        }
    }

    @Test void fullRoundsUseOneProbeAndOneBulkInsertionPerDestination() {
        var source = inventory(64, 0);
        var a = inventory(0, 0); var b = inventory(0, 0);
        var calls = new int[1];
        var handlers = List.of(counted(a, calls), counted(b, calls));
        assertEquals(64, FairItemTransfer.batchFullRounds(VanillaContainerWrapper.of(source), handlers, 64, (i, r) -> true));
        assertEquals(4, calls[0]);
        assertEquals(0, source.getItem(0).getCount());
        assertEquals(32, a.getItem(0).getCount()); assertEquals(32, b.getItem(0).getCount());
    }

    @Test void bulkOnlyDestinationDoesNotBypassOriginalSingleItemRestriction() {
        var source = inventory(64, 0); var output = inventory(0, 0);
        var bulkOnly = new Forwarding(VanillaContainerWrapper.of(output)) {
            @Override public int insert(ItemResource resource, int amount, TransactionContext tx) {
                return amount < 2 ? 0 : super.insert(resource, amount, tx);
            }
        };
        assertEquals(0, FairItemTransfer.batchFullRounds(VanillaContainerWrapper.of(source),
                List.of(bulkOnly), 64, (i, r) -> true));
        assertEquals(64, source.getItem(0).getCount()); assertTrue(output.isEmpty());
    }

    @Test void bulkOnlyExtractionDoesNotBypassOriginalSingleItemRestriction() {
        var input = inventory(64, 0); var output = inventory(0, 0);
        var source = new Forwarding(VanillaContainerWrapper.of(input)) {
            @Override public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
                return amount < 2 ? 0 : super.extract(slot, resource, amount, tx);
            }
        };
        assertEquals(0, FairItemTransfer.batchFullRounds(source,
                List.of(VanillaContainerWrapper.of(output)), 64, (i, r) -> true));
        assertEquals(64, input.getItem(0).getCount()); assertTrue(output.isEmpty());
    }

    @Test void rejectedDestinationRollsBackEarlierInsertions() {
        var source = inventory(64, 0);
        var a = inventory(0, 0); var b = inventory(63, 64);
        assertEquals(0, FairItemTransfer.batchFullRounds(VanillaContainerWrapper.of(source),
                List.of(VanillaContainerWrapper.of(a), VanillaContainerWrapper.of(b)), 64, (i, r) -> true));
        assertEquals(64, source.getItem(0).getCount()); assertTrue(a.isEmpty());
        assertEquals(63, b.getItem(0).getCount()); assertEquals(64, b.getItem(1).getCount());
    }

    @Test void sameItemWithDifferentComponentsKeepsItsPerTargetDistribution() {
        var source = new SimpleContainer(2);
        var first = new ItemStack(Items.DIAMOND, 64);
        var second = new ItemStack(Items.DIAMOND, 64);
        first.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("first"));
        second.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("second"));
        source.setItem(0, first); source.setItem(1, second);
        var a = inventory(0, 0); var b = inventory(0, 0);
        assertEquals(128, FairItemTransfer.batchFullRounds(VanillaContainerWrapper.of(source),
                List.of(VanillaContainerWrapper.of(a), VanillaContainerWrapper.of(b)), 128, (i, r) -> true));
        assertTrue(source.isEmpty());
        for (var output : List.of(a, b)) {
            assertEquals(32, output.getItem(0).getCount()); assertEquals(32, output.getItem(1).getCount());
            assertEquals("first", output.getItem(0).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
            assertEquals("second", output.getItem(1).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
        }
    }

    @Test void extractionMismatchRollsBackAllDestinations() {
        var sourceInventory = inventory(64, 0);
        var source = new Forwarding(VanillaContainerWrapper.of(sourceInventory)) {
            @Override public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
                return super.extract(index, resource, Math.min(1, amount), tx);
            }
        };
        var a = inventory(0, 0); var b = inventory(0, 0);
        assertEquals(0, FairItemTransfer.batchFullRounds(source,
                List.of(VanillaContainerWrapper.of(a), VanillaContainerWrapper.of(b)), 64, (i, r) -> true));
        assertEquals(64, sourceInventory.getItem(0).getCount()); assertTrue(a.isEmpty()); assertTrue(b.isEmpty());
    }

    @Test void exceptionRollsBackRatherThanDuplicatingItems() {
        var source = inventory(64, 0); var a = inventory(0, 0);
        var throwing = new Forwarding(VanillaContainerWrapper.of(inventory(0, 0))) {
            @Override public int insert(ItemResource resource, int amount, TransactionContext tx) {
                throw new IllegalStateException("fixture");
            }
        };
        assertThrows(IllegalStateException.class, () -> FairItemTransfer.batchFullRounds(VanillaContainerWrapper.of(source),
                List.of(VanillaContainerWrapper.of(a), throwing), 64, (i, r) -> true));
        assertEquals(64, source.getItem(0).getCount()); assertTrue(a.isEmpty());
    }

    @Test void randomInventoriesMatchOriginalItemOrderFiltersBudgetAndCursor() {
        var random = new Random(20261017);
        for (int scenario = 0; scenario < 1000; scenario++) {
            var originalSource = inventory(random.nextInt(65), random.nextInt(65));
            var optimizedSource = copy(originalSource);
            int size = 1 + random.nextInt(6), budget = random.nextInt(129), start = random.nextInt(size);
            var originalDest = new ArrayList<ResourceHandler<ItemResource>>();
            var optimizedDest = new ArrayList<ResourceHandler<ItemResource>>();
            var inventories = new ArrayList<SimpleContainer>(); var copies = new ArrayList<SimpleContainer>();
            int[] filter = new int[size];
            for (int i = 0; i < size; i++) {
                var inventory = inventory(random.nextInt(65), random.nextInt(65)); var clone = copy(inventory);
                inventories.add(inventory); copies.add(clone);
                originalDest.add(VanillaContainerWrapper.of(inventory)); optimizedDest.add(VanillaContainerWrapper.of(clone));
                filter[i] = random.nextInt(3);
            }
            BiPredicate<Integer, ItemResource> accepts = (i, r) -> filter[i] == 0
                    || (filter[i] == 1 ? r.is(Items.DIAMOND) : r.is(Items.EMERALD));
            int expectedCursor = original(VanillaContainerWrapper.of(originalSource), originalDest, budget, start, accepts);
            var ordered = new ArrayList<ResourceHandler<ItemResource>>();
            for (int i = 0; i < size; i++) ordered.add(optimizedDest.get((start + i) % size));
            int batched = FairItemTransfer.batchFullRounds(VanillaContainerWrapper.of(optimizedSource), ordered,
                    budget, (i, resource) -> accepts.test((start + i) % size, resource));
            int cursor = batched == budget ? start : original(VanillaContainerWrapper.of(optimizedSource),
                    optimizedDest, budget - batched, start, accepts);
            assertEquals(expectedCursor, cursor, "cursor scenario " + scenario);
            assertInventory(originalSource, optimizedSource, scenario);
            for (int i = 0; i < size; i++) assertInventory(inventories.get(i), copies.get(i), scenario);
        }
    }

    @Test void repeatedRejectedResourcesAreProbedOncePerMoveAndRetriedOnNextMove() {
        var input = new SimpleContainer(8);
        for (int i = 0; i < 8; i++) input.setItem(i, new ItemStack(Items.DIAMOND, 64));
        var calls = new int[1];
        var target = new Forwarding(VanillaContainerWrapper.of(inventory(0, 0))) {
            @Override public int insert(ItemResource resource, int amount, TransactionContext tx) {
                calls[0]++; return 0;
            }
        };
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertEquals(0, RejectedItemTransfer.moveOne(VanillaContainerWrapper.of(input), target, r -> true, null));
            assertEquals(attempt, calls[0]);
        }
        for (int i = 0; i < 8; i++) assertEquals(64, input.getItem(i).getCount());
    }

    @Test void failedExtractionDoesNotHideLaterExtractableSlotWithSameResource() {
        var input = new SimpleContainer(2);
        input.setItem(0, new ItemStack(Items.DIAMOND, 64)); input.setItem(1, new ItemStack(Items.DIAMOND, 64));
        var source = new Forwarding(VanillaContainerWrapper.of(input)) {
            @Override public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
                return slot == 0 ? 0 : super.extract(slot, resource, amount, tx);
            }
        };
        var output = inventory(0, 0);
        assertEquals(1, RejectedItemTransfer.moveOne(source, VanillaContainerWrapper.of(output), r -> true, null));
        assertEquals(64, input.getItem(0).getCount()); assertEquals(63, input.getItem(1).getCount());
        assertEquals(1, output.getItem(0).getCount());
    }

    @Test void rejectionKeyIncludesComponents() {
        var input = new SimpleContainer(2);
        input.setItem(0, new ItemStack(Items.DIAMOND, 64));
        var named = new ItemStack(Items.DIAMOND, 64);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("allowed"));
        input.setItem(1, named);
        var output = inventory(0, 0);
        var target = new Forwarding(VanillaContainerWrapper.of(output)) {
            @Override public int insert(ItemResource resource, int amount, TransactionContext tx) {
                return resource.toStack().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                        ? super.insert(resource, amount, tx) : 0;
            }
        };
        assertEquals(1, RejectedItemTransfer.moveOne(VanillaContainerWrapper.of(input), target, r -> true, null));
        assertEquals(64, input.getItem(0).getCount()); assertEquals(63, input.getItem(1).getCount());
        assertEquals("allowed", output.getItem(0).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
    }

    @Test void repeatedRejectionPathMatchesOriginalForRandomMixedSlotsAndCapacity() {
        var random = new Random(20261018);
        for (int scenario = 0; scenario < 1000; scenario++) {
            var input = new SimpleContainer(12);
            for (int i = 0; i < 12; i++) {
                if (random.nextBoolean()) input.setItem(i, new ItemStack(random.nextBoolean() ? Items.DIAMOND : Items.EMERALD, 1 + random.nextInt(64)));
            }
            var clone = copy(input); var a = inventory(0, random.nextInt(65)); var b = copy(a);
            java.util.function.Function<SimpleContainer, ResourceHandler<ItemResource>> onlyEmerald = inv -> new Forwarding(VanillaContainerWrapper.of(inv)) {
                @Override public int insert(ItemResource resource, int amount, TransactionContext tx) {
                    return resource.is(Items.EMERALD) ? super.insert(resource, amount, tx) : 0;
                }
            };
            int expected = ResourceHandlerUtil.move(VanillaContainerWrapper.of(input), onlyEmerald.apply(a), r -> true, 1, null);
            int actual = RejectedItemTransfer.moveOne(VanillaContainerWrapper.of(clone), onlyEmerald.apply(b), r -> true, null);
            assertEquals(expected, actual); assertInventory(input, clone, scenario); assertInventory(a, b, scenario);
        }
    }

    @Test void rejectionObserverKeepsOriginalExceptionRollback() {
        var input = inventory(64, 0); var output = inventory(0, 0);
        var target = new Forwarding(VanillaContainerWrapper.of(output)) {
            @Override public int insert(ItemResource resource, int amount, TransactionContext tx) {
                super.insert(resource, amount, tx); throw new IllegalStateException("fixture");
            }
        };
        assertThrows(net.minecraft.ReportedException.class, () -> RejectedItemTransfer.moveOne(
                VanillaContainerWrapper.of(input), target, r -> true, null));
        assertEquals(64, input.getItem(0).getCount()); assertTrue(output.isEmpty());
    }

    // Reference: original Pipez traversal and one-item ResourceHandlerUtil.move calls.
    private static int original(ResourceHandler<ItemResource> source, List<ResourceHandler<ItemResource>> dest,
            int budget, int start, BiPredicate<Integer, ItemResource> accepts) {
        boolean[] exhausted = new boolean[dest.size()]; int active = dest.size(), p = start;
        while (budget > 0 && active > 0) {
            int moved = 0; var target = dest.get(p); final int index = p;
            if (target != null && !exhausted[p] && !ResourceHandlerUtil.isFull(target)) {
                moved = ResourceHandlerUtil.move(source, target, resource -> accepts.test(index, resource), 1, null);
            }
            if (moved > 0) budget -= moved;
            else if (!exhausted[p]) { exhausted[p] = true; active--; }
            p = (p + 1) % dest.size();
        }
        return p;
    }

    private static SimpleContainer inventory(int diamonds, int emeralds) {
        var result = new SimpleContainer(2);
        if (diamonds > 0) result.setItem(0, new ItemStack(Items.DIAMOND, diamonds));
        if (emeralds > 0) result.setItem(1, new ItemStack(Items.EMERALD, emeralds));
        return result;
    }
    private static SimpleContainer copy(SimpleContainer source) {
        var result = new SimpleContainer(source.getContainerSize());
        for (int i = 0; i < source.getContainerSize(); i++) result.setItem(i, source.getItem(i).copy());
        return result;
    }
    private static void assertInventory(SimpleContainer a, SimpleContainer b, int scenario) {
        for (int i = 0; i < a.getContainerSize(); i++) {
            assertTrue(ItemStack.matches(a.getItem(i), b.getItem(i)), "inventory scenario " + scenario + " slot " + i);
        }
    }
    private static ResourceHandler<ItemResource> counted(SimpleContainer inventory, int[] calls) {
        return new Forwarding(VanillaContainerWrapper.of(inventory)) {
            @Override public int insert(ItemResource resource, int amount, TransactionContext tx) {
                calls[0]++; return super.insert(resource, amount, tx);
            }
        };
    }
    private static class Forwarding implements ResourceHandler<ItemResource> {
        final ResourceHandler<ItemResource> delegate;
        Forwarding(ResourceHandler<ItemResource> delegate) { this.delegate = delegate; }
        public int size() { return delegate.size(); }
        public ItemResource getResource(int i) { return delegate.getResource(i); }
        public long getAmountAsLong(int i) { return delegate.getAmountAsLong(i); }
        public long getCapacityAsLong(int i, ItemResource r) { return delegate.getCapacityAsLong(i, r); }
        public boolean isValid(int i, ItemResource r) { return delegate.isValid(i, r); }
        public int insert(int i, ItemResource r, int n, TransactionContext tx) { return delegate.insert(i, r, n, tx); }
        public int extract(int i, ItemResource r, int n, TransactionContext tx) { return delegate.extract(i, r, n, tx); }
        public int insert(ItemResource r, int n, TransactionContext tx) { return delegate.insert(r, n, tx); }
    }
}
