package cn.zedocn.tech.mixin;

import cn.zedocn.tech.performance.FairItemTransfer;
import cn.zedocn.tech.performance.RejectedItemTransfer;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.maxhenkel.pipez.Filter;
import de.maxhenkel.pipez.blocks.tileentity.PipeLogicTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.PipeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.UpgradeTileEntity;
import de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType", remap = false)
public abstract class PipezItemMixin {
    @Unique private final ThreadLocal<Integer> tech$remainingBudget = ThreadLocal.withInitial(() -> -1);

    @Shadow private boolean canInsert(HolderLookup.Provider provider, PipeTileEntity.Connection connection,
            ItemStack stack, List<Filter<?, ?>> filters) { throw new AssertionError(); }

    @WrapMethod(method = "insertEqually")
    private void tech$batchRounds(PipeLogicTileEntity tile, Direction side,
            List<PipeTileEntity.Connection> connections, ResourceHandler<ItemResource> source,
            Operation<Void> original) {
        if (connections.isEmpty()) return;
        ItemPipeType self = (ItemPipeType) (Object) this;
        int budget = self.getRate(tile, side);
        int count = connections.size();
        if (budget / count < 2) {
            original.call(tile, side, connections, source);
            return;
        }
        int start = Math.floorMod(tile.getRoundRobinIndex(side, self), count);
        var ordered = new ArrayList<PipeTileEntity.Connection>(count);
        var handlers = new ArrayList<ResourceHandler<ItemResource>>(count);
        for (int i = 0; i < count; i++) {
            var connection = connections.get((start + i) % count);
            ordered.add(connection);
            handlers.add(connection.getItemHandler());
        }
        var filters = tile.getFilters(side, self);
        boolean blacklist = tile.getFilterMode(side, self) == UpgradeTileEntity.FilterMode.BLACKLIST;
        int moved = FairItemTransfer.batchFullRounds(source, handlers, budget,
                (index, resource) -> canInsert(tile.getLevel().registryAccess(), ordered.get(index),
                        resource.toStack(), filters) != blacklist);
        if (moved == 0) {
            original.call(tile, side, connections, source);
            return;
        }
        // Complete rounds leave the next destination unchanged, including when
        // changing from one source item/component set to another.
        tile.setRoundRobinIndex(side, self, start);
        if (moved == budget) return;
        int previous = tech$remainingBudget.get();
        tech$remainingBudget.set(budget - moved);
        try {
            original.call(tile, side, connections, source);
        } finally {
            if (previous < 0) tech$remainingBudget.remove();
            else tech$remainingBudget.set(previous);
        }
    }

    @WrapOperation(method = "insertEqually", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/transfer/ResourceHandlerUtil;move(Lnet/neoforged/neoforge/transfer/ResourceHandler;Lnet/neoforged/neoforge/transfer/ResourceHandler;Ljava/util/function/Predicate;ILnet/neoforged/neoforge/transfer/transaction/TransactionContext;)I"))
    private int tech$skipRepeatedRejections(ResourceHandler<ItemResource> source,
            ResourceHandler<ItemResource> target, Predicate<ItemResource> filter,
            int amount, TransactionContext parent, Operation<Integer> original) {
        if (amount != 1 || source.size() < 2) return original.call(source, target, filter, amount, parent);
        return RejectedItemTransfer.moveOne(source, target, filter, parent);
    }

    @ModifyExpressionValue(method = "insertEqually", at = @At(value = "INVOKE",
            target = "Lde/maxhenkel/pipez/blocks/tileentity/types/ItemPipeType;getRate(Lde/maxhenkel/pipez/blocks/tileentity/PipeLogicTileEntity;Lnet/minecraft/core/Direction;)I"))
    private int tech$remainingRate(int original) {
        int remaining = tech$remainingBudget.get();
        if (remaining >= 0) tech$remainingBudget.remove();
        return remaining < 0 ? original : remaining;
    }
}
