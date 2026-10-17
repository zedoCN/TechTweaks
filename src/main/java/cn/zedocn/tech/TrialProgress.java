package cn.zedocn.tech;

import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;

public final class TrialProgress {
    private TrialProgress() {}

    public static int status(TrialSpawnerState state, int failedSpawns, int total, int target) {
        return switch (state) {
            case ACTIVE -> total >= target ? 12 : failedSpawns >= 100 ? 11 : 9;
            case WAITING_FOR_REWARD_EJECTION, EJECTING_REWARD -> 13;
            case COOLDOWN -> 14;
            case INACTIVE -> 15;
            case WAITING_FOR_PLAYERS -> 10;
        };
    }
}
