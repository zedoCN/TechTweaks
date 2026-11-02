package cn.zedocn.tech.mixin;

import com.hbm.api.fluidmk2.IFluidHandlerMK2;
import com.hbm.blockentity.machine.storage.BlockEntityMachineFluidTank;
import com.hbm.blockentity.machine.storage.BlockEntityMachineOrbus;
import com.hbm.capability.MachineFluidHandler;
import com.hbm.capability.NtmCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** External storage adapters can use either native open fluid face in both directions.
 * The tank's mode, pressure and type still determine whether a transfer is allowed. */
@Mixin(value = MachineFluidHandler.class, remap = false)
public abstract class TankFluidFaceMixin {
    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private static void tech$bidirectionalTankFace(Level level, BlockPos pos, Direction side,
            Fluid fluid, boolean receiver, CallbackInfoReturnable<IFluidHandlerMK2> result) {
        if (result.getReturnValue() != null) return;
        var owner = NtmCapabilities.fullSurfaceCap(level, pos, level.getBlockState(pos), side,
                IFluidHandlerMK2.class, receiver ? NtmCapabilities.CapRole.FLUID_OUT : NtmCapabilities.CapRole.FLUID_IN);
        if (owner instanceof BlockEntityMachineFluidTank || owner instanceof BlockEntityMachineOrbus)
            result.setReturnValue(owner);
    }
}
