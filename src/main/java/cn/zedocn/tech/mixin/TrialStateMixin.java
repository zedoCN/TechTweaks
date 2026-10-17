package cn.zedocn.tech.mixin;
import cn.zedocn.tech.ControlledTrial;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(TrialSpawnerState.class)
public abstract class TrialStateMixin {
    @Redirect(method="tickAndGetNext",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData;countAdditionalPlayers(Lnet/minecraft/core/BlockPos;)I"))
    private int techCount(TrialSpawnerStateData data,BlockPos dataPos,BlockPos pos,TrialSpawner spawner,ServerLevel level) {
        int players=((ControlledTrial)(Object)spawner).techPlayers();
        return players>0 ? players-1 : data.countAdditionalPlayers(dataPos);
    }
}
