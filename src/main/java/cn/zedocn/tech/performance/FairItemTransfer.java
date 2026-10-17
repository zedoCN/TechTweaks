package cn.zedocn.tech.performance;

import java.util.List;
import java.util.function.BiPredicate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Batches complete round-robin rounds of one source slot; leaves partial rounds to Pipez. */
public final class FairItemTransfer {
    private FairItemTransfer() {}

    public static int batchFullRounds(ResourceHandler<ItemResource> source,
            List<ResourceHandler<ItemResource>> destinations, int budget,
            BiPredicate<Integer, ItemResource> accepts) {
        int count = destinations.size();
        if (count == 0 || budget / count < 2) return 0;
        int moved = 0;
        while ((budget - moved) / count >= 2) {
            int slot = 0;
            while (slot < source.size()
                    && (source.getResource(slot).isEmpty() || source.getAmountAsLong(slot) == 0)) slot++;
            if (slot == source.size()) break;
            ItemResource resource = source.getResource(slot);
            int each = (int) Math.min((budget - moved) / count, source.getAmountAsLong(slot) / count);
            if (each < 2) break;
            for (int i = 0; i < count; i++) {
                var destination = destinations.get(i);
                if (destination == null || destination == source || !accepts.test(i, resource)
                        || ResourceHandlerUtil.isFull(destination)) return moved;
            }
            int total = each * count;
            // A rejected batch rolls back every insertion. The original one-item
            // algorithm then handles restrictive slots, filters and partial capacity.
            try (var transaction = Transaction.openRoot()) {
                // Preserve handlers that reject single-item transfers (e.g. a
                // recipe/exact-amount port); accepting a bulk request is not sufficient.
                try (var probe = Transaction.open(transaction)) {
                    if (source.extract(slot, resource, 1, probe) != 1) return moved;
                }
                for (var destination : destinations) {
                    try (var probe = Transaction.open(transaction)) {
                        if (destination.insert(resource, 1, probe) != 1) return moved;
                    }
                }
                for (var destination : destinations) {
                    if (destination.insert(resource, each, transaction) != each) return moved;
                }
                if (source.extract(slot, resource, total, transaction) != total) return moved;
                transaction.commit();
            }
            moved += total;
            PerformanceFixes.pipeBatch(total);
        }
        return moved;
    }
}
