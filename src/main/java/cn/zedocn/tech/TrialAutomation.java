package cn.zedocn.tech;

import java.util.UUID;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import cn.zedocn.tech.mixin.TrialDataAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.trialspawner.*;

public final class TrialAutomation {
    public static void tick(ServerLevel level, ControllerBlockEntity controller) {
        var pos=controller.targetPos();
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity entity)
                || AutomationLookup.controller(level,pos,ControllerKind.TRIAL)!=controller) { controller.status=1; return; }
        var spawner=entity.getTrialSpawner();
        var round=(ControlledTrial)(Object)spawner;
        var data=(TrialDataAccess)spawner.getStateData();
        if (!spawner.canSpawnInLevel(level)) { controller.status=6; return; }
        if (!spawner.getStateData().hasMobToSpawn(spawner,level.getRandom())) { controller.status=15; return; }
        if (spawner.getState()==TrialSpawnerState.WAITING_FOR_PLAYERS && round.techPlayers()==0
                && data.techCurrentMobs().isEmpty() && data.techDetectedPlayers().isEmpty()) {
            start(level,controller);
            return;
        }
        controller.status=TrialProgress.status(spawner.getState(),round.techFailedSpawns(),
                data.techTotalMobs(),spawner.activeConfig().calculateTargetTotalMobs(Math.max(0,round.techPlayers()-1)));
    }
    public static boolean start(ServerLevel level, ControllerBlockEntity controller) {
        var pos=controller.targetPos();
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity entity)) { controller.status=1; return false; }
        if (AutomationLookup.controller(level,pos,ControllerKind.TRIAL)!=controller) { controller.status=1; return false; }
        TrialSpawner spawner=entity.getTrialSpawner();
        var round=(ControlledTrial)(Object)spawner;
        var data=(TrialDataAccess)spawner.getStateData();
        if (spawner.getState()!=TrialSpawnerState.WAITING_FOR_PLAYERS || round.techPlayers()!=0 || !spawner.getStateData().haveAllCurrentMobsDied() || !data.techDetectedPlayers().isEmpty()) { controller.status=3; return false; }
        if (!spawner.canSpawnInLevel(level)) { controller.status=6; return false; }
        if (!spawner.getStateData().hasMobToSpawn(spawner,level.getRandom())) { controller.status=15; return false; }
        if (controller.ominous && !controller.getItem(0).is(Items.OMINOUS_BOTTLE)) { controller.status=4; return false; }
        int players=Balance.players(controller.players);
        if (!controller.spend(Balance.trialCost(players,controller.ominous))) return false;
        if (controller.ominous) { spawner.applyOminous(level,pos); controller.removeItem(0,1); }
        // A single saved virtual participant drives the vanilla round. No FakePlayer entity or online-count change.
        round.techPlayers(players);
        data.techDetectedPlayers().add(UUID.randomUUID());
        data.techNextSpawn(level.getGameTime()+40);
        spawner.setState(level,TrialSpawnerState.ACTIVE); spawner.markUpdated(); entity.setChanged();
        level.levelEvent(spawner.isOminous()?3019:3013,pos,players);
        controller.status=0; controller.setChanged(); return true;
    }
}
