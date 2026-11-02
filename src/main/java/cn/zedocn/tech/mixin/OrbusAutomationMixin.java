package cn.zedocn.tech.mixin;

import cn.zedocn.tech.integration.TankAutomation;
import com.hbm.blockentity.machine.storage.BlockEntityBarrel;
import com.hbm.blockentity.machine.storage.BlockEntityMachineOrbus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockEntityBarrel.class, remap = false)
public abstract class OrbusAutomationMixin {
    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void tech$routeContainers(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if ((Object)this instanceof BlockEntityMachineOrbus machine && (slot == 2 || slot == 4))
            result.setReturnValue(TankAutomation.accepts(machine.tank, slot, stack));
    }

    @Inject(method = "getDemand", at = @At("HEAD"), cancellable = true)
    private void tech$firstFluid(Fluid fluid, int pressure, CallbackInfoReturnable<Long> result) {
        if ((Object)this instanceof BlockEntityMachineOrbus machine && !machine.isTilted()
                && (machine.getMode() == 0 || machine.getMode() == 1)
                && TankAutomation.untypedInput(machine.tank, fluid, pressure))
            result.setReturnValue((long)machine.tank.getMaxFill());
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void tech$primeContainer(CallbackInfo info) {
        if ((Object)this instanceof BlockEntityMachineOrbus machine)
            TankAutomation.primeFromContainer(machine, machine.tank);
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void tech$finishContainers(CallbackInfo info) {
        if ((Object)this instanceof BlockEntityMachineOrbus machine) TankAutomation.finish(machine, machine.tank);
    }
}
