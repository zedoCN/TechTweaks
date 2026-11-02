package cn.zedocn.tech.mixin;

import com.hbm.inventory.fluid.tank.FluidTankNTM;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = FluidTankNTM.class, remap = false)
public interface FluidTankNativeAccess {
    @Invoker("serveFillableMods")
    void tech$serveFillableMods(ItemStack armor, boolean drainMods);
}
