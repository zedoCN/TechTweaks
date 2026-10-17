package cn.zedocn.tech.mixin;
import cn.zedocn.tech.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TrialSpawnerStateData.class)
public abstract class TrialDataMixin {
    @Inject(method="tryDetectPlayers",at=@At("HEAD"),cancellable=true)
    private void techDetection(ServerLevel level,BlockPos pos,TrialSpawner spawner,CallbackInfo ci) {
        if (((ControlledTrial)(Object)spawner).techPlayers()>0 || AutomationLookup.controller(level,pos,ControllerKind.TRIAL)!=null) ci.cancel();
    }
}
