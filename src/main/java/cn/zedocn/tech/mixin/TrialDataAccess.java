package cn.zedocn.tech.mixin;
import java.util.*;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(TrialSpawnerStateData.class)
public interface TrialDataAccess {
    @Accessor("detectedPlayers") Set<UUID> techDetectedPlayers();
    @Accessor("nextMobSpawnsAt") void techNextSpawn(long value);
    @Accessor("currentMobs") Set<UUID> techCurrentMobs();
    @Accessor("totalMobsSpawned") int techTotalMobs();
}
