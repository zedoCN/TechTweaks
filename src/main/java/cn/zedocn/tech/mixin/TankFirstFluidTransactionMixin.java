package cn.zedocn.tech.mixin;

import cn.zedocn.tech.integration.TankAutomation;
import com.hbm.blockentity.machine.storage.BlockEntityMachineFluidTank;
import com.hbm.blockentity.machine.storage.BlockEntityMachineOrbus;
import com.hbm.capability.ForeignFluidStaging;
import com.hbm.inventory.fluid.tank.FluidTankNTM;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses NTM's shared transaction journal; choosing a fluid happens only at commit. */
@Mixin(value = ForeignFluidStaging.class, remap = false)
public abstract class TankFirstFluidTransactionMixin {
    @Shadow @Final private BlockEntity be;
    @Shadow @Final private Fluid[] staged;

    @Inject(method = "addressable", at = @At("HEAD"), cancellable = true)
    private void tech$firstFluid(FluidTankNTM[] tanks, int index, Fluid fluid, boolean inserts,
                                CallbackInfoReturnable<Boolean> result) {
        if (!inserts || !(be instanceof BlockEntityMachineFluidTank || be instanceof BlockEntityMachineOrbus)) return;
        if (TankAutomation.untypedInput(tanks[index], fluid, 0))
            result.setReturnValue(staged[index] == null || staged[index] == fluid);
    }
}
