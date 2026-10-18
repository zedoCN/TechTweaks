package cn.zedocn.tech.server.performance;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Reuses only zero insert results within one single-item move, before any commit. */
public final class RejectedItemTransfer {
    private RejectedItemTransfer() {}

    public static int moveOne(ResourceHandler<ItemResource> source, ResourceHandler<ItemResource> target,
            Predicate<ItemResource> filter, TransactionContext parent) {
        Set<ItemResource> rejected = new HashSet<>();
        var observed = new ObservedTarget(target, rejected);
        // A failed extraction never enters rejected: another slot with the same
        // resource may still be extractable. Component sets are part of the key.
        return ResourceHandlerUtil.move(source, observed, resource -> {
            if (rejected.contains(resource)) {
                PerformanceFixes.rejectedProbeSkipped();
                return false;
            }
            return filter.test(resource);
        }, 1, parent);
    }

    private record ObservedTarget(ResourceHandler<ItemResource> delegate, Set<ItemResource> rejected)
            implements ResourceHandler<ItemResource> {
        public int size() { return delegate.size(); }
        public ItemResource getResource(int index) { return delegate.getResource(index); }
        public long getAmountAsLong(int index) { return delegate.getAmountAsLong(index); }
        public long getCapacityAsLong(int index, ItemResource resource) { return delegate.getCapacityAsLong(index, resource); }
        public boolean isValid(int index, ItemResource resource) { return delegate.isValid(index, resource); }
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            return delegate.insert(index, resource, amount, tx);
        }
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            return delegate.extract(index, resource, amount, tx);
        }
        @Override public int insert(ItemResource resource, int amount, TransactionContext tx) {
            int inserted = delegate.insert(resource, amount, tx);
            if (amount == 1 && inserted == 0) rejected.add(resource);
            return inserted;
        }
        @Override public String toString() { return delegate.toString(); }
    }
}
