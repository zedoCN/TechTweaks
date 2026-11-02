package cn.zedocn.tech.mixin;

import com.hbm.blockentity.machine.fusion.BlockEntityFusionPlasmaForge;
import com.hbm.capability.port.ItemPort;
import com.hbm.inventory.fluid.tank.FluidTankNTM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockEntityFusionPlasmaForge.class, remap = false)
public abstract class PlasmaForgeAutomationMixin {
    @Unique private ItemPort tech$originalPort;
    @Unique private ItemPort tech$automationPort;

    // The foreign-fluid adapter enumerates getAllTanks(), not getReceivingTanks().
    // NTM's default is empty, which hides this machine from AE/Pipez completely.
    public FluidTankNTM[] getAllTanks() {
        return ((BlockEntityFusionPlasmaForge)(Object)this).getReceivingTanks();
    }

    @Inject(method = "itemAccess", at = @At("RETURN"), cancellable = true)
    private void tech$recipeInputs(BlockPos pos, Direction side, CallbackInfoReturnable<ItemPort> result) {
        ItemPort original = result.getReturnValue();
        if (original == null || original.isEmpty()) return;
        if (tech$originalPort != original) {
            var machine = (BlockEntityFusionPlasmaForge)(Object)this;
            int[] slots = java.util.stream.IntStream.concat(java.util.Arrays.stream(original.slots()),
                    java.util.stream.IntStream.range(2, 15)).distinct().toArray();
            var nativeSlots = new java.util.BitSet();
            for (int slot : original.slots()) nativeSlots.set(slot);
            tech$automationPort = new ItemPort(slots,
                    (slot, stack) -> (nativeSlots.get(slot) && original.canInsert(slot, stack))
                            || (slot >= 2 && slot < 15 && machine.canPlaceItem(slot, stack)),
                    (slot, stack) -> nativeSlots.get(slot) && original.canExtract(slot, stack));
            tech$originalPort = original;
        }
        result.setReturnValue(tech$automationPort);
    }
}
