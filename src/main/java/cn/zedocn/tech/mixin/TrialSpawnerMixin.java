package cn.zedocn.tech.mixin;
import cn.zedocn.tech.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin implements ControlledTrial {
    @Unique private int tech$players;
    @Unique private int tech$failedSpawns;
    @Unique private boolean tech$repeatingReward;
    public int techPlayers() { return tech$players; }
    public void techPlayers(int p) { tech$players=p==0?0:Balance.players(p); tech$failedSpawns=0; }
    public int techFailedSpawns() { return tech$failedSpawns; }
    public void techSpawnResult(boolean success) { tech$failedSpawns=success?0:Math.min(100,tech$failedSpawns+1); }
    @Inject(method="spawnMob",at=@At("RETURN"))
    private void techSpawnResult(ServerLevel level,BlockPos pos,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.Optional<java.util.UUID>> cir) {
        if (tech$players>0) techSpawnResult(cir.getReturnValue().isPresent());
    }
    @Inject(method="store",at=@At("TAIL"))
    private void techStore(ValueOutput out,CallbackInfo ci) { out.putInt("tech_round_players",tech$players); }
    @Inject(method="load",at=@At("TAIL"))
    private void techLoad(ValueInput in,CallbackInfo ci) { techPlayers(in.getIntOr("tech_round_players",0)); }
    @Inject(method="tickServer",at=@At("TAIL"))
    private void techCooldown(ServerLevel level,BlockPos pos,boolean ominous,CallbackInfo ci) {
        TrialSpawner self=(TrialSpawner)(Object)this;
        if (tech$players==0 && self.getState()!=TrialSpawnerState.COOLDOWN) return;
        if (self.getState()==TrialSpawnerState.COOLDOWN && (tech$players>0 || AutomationLookup.controller(level,pos,ControllerKind.TRIAL)!=null)) {
            self.removeOminous(level,pos); self.getStateData().reset(); tech$players=0;
            self.setState(level,TrialSpawnerState.WAITING_FOR_PLAYERS); self.markUpdated();
        } else if ((self.getState()==TrialSpawnerState.WAITING_FOR_PLAYERS || self.getState()==TrialSpawnerState.INACTIVE) && ((TrialDataAccess)self.getStateData()).techDetectedPlayers().isEmpty()) {
            tech$players=0; self.markUpdated();
        }
    }
    @Inject(method="ejectReward",at=@At("TAIL"))
    private void techReward(ServerLevel level,BlockPos pos,ResourceKey<LootTable> table,CallbackInfo ci) {
        if (tech$players<=1 || tech$repeatingReward) return;
        tech$repeatingReward=true;
        try { for(int i=1;i<tech$players;i++) ((TrialSpawner)(Object)this).ejectReward(level,pos,table); }
        finally { tech$repeatingReward=false; }
    }
}
