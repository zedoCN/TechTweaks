package cn.zedocn.tech.mixin;

import cn.zedocn.tech.integration.TankAutomation;
import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blockentity.machine.storage.BlockEntityMachineFluidTank;
import com.hbm.inventory.fluid.tank.FluidTankNTM;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockEntityMachineFluidTank.class, remap = false)
public abstract class FluidTankAutomationMixin extends BlockEntityMachineBase {
    @Shadow @Final public FluidTankNTM tank;

    protected FluidTankAutomationMixin(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state, slots);
    }

    @Override public int[] getSlotsForFace(Direction side) { return TankAutomation.SLOTS; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == 3 || slot == 5;
    }

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void tech$routeContainers(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (slot == 2 || slot == 4) result.setReturnValue(TankAutomation.accepts(tank, slot, stack));
    }

    @Inject(method = "getDemand", at = @At("HEAD"), cancellable = true)
    private void tech$firstFluid(Fluid fluid, int pressure, CallbackInfoReturnable<Long> result) {
        var machine = (BlockEntityMachineFluidTank)(Object)this;
        if (!machine.isDamaged() && (machine.mode == 0 || machine.mode == 1)
                && TankAutomation.untypedInput(tank, fluid, pressure)) result.setReturnValue((long)tank.getMaxFill());
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void tech$primeContainer(CallbackInfo info) { TankAutomation.primeFromContainer(this, tank); }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void tech$finishContainers(CallbackInfo info) { TankAutomation.finish(this, tank); }
}
