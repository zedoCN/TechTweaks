package cn.zedocn.tech.server.mixin;

import cn.zedocn.tech.server.performance.PerformanceFixes;
import com.hbm.world.feature.OilSurfaceScarring;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = OilSurfaceScarring.class, remap = false)
public abstract class OilSurfaceMixin {
    // NTM 1.0.1 / 1.0.3: local 13 is ry, initialized after random rx/rz.
    // Change only the start of the non-air search, keeping all random draws,
    // surface edits, production and fluid/power consumption in the original method.
    @ModifyVariable(method = "generateOilSpot", at = @At("STORE"), index = 13)
    private static int tech$surfaceStart(int original, LevelAccessor level, RandomSource random,
            int x, int z, int width, int count, @Local(index = 11) int rx, @Local(index = 12) int rz) {
        return PerformanceFixes.surfaceY(level, rx, rz);
    }
}
