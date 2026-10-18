package cn.zedocn.tech.server.mixin;

import cn.zedocn.tech.server.performance.PerformanceFixes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public abstract class FallingLeafMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void tech$discardFailedOldLeaf(CallbackInfo callback) {
        PerformanceFixes.discardStaleLeaves((FallingBlockEntity) (Object) this);
    }
}
