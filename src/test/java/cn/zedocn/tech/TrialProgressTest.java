package cn.zedocn.tech;

import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrialProgressTest {
    @Test void idleIsReadyToStartRatherThanRunning() {
        assertEquals(10,TrialProgress.status(TrialSpawnerState.WAITING_FOR_PLAYERS,0,0,6));
    }
    @Test void randomFailedAttemptsDoNotImmediatelyReportBlocked() {
        assertEquals(9,TrialProgress.status(TrialSpawnerState.ACTIVE,99,0,6));
        assertEquals(11,TrialProgress.status(TrialSpawnerState.ACTIVE,100,0,6));
    }
    @Test void completedSpawnQuotaWaitsForCombatEvenAfterEarlierFailures() {
        assertEquals(12,TrialProgress.status(TrialSpawnerState.ACTIVE,100,6,6));
        assertEquals(13,TrialProgress.status(TrialSpawnerState.EJECTING_REWARD,100,0,6));
    }
}
