package cn.zedocn.tech;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class AreaProtection {
    private static final Set<ControllerBlockEntity> loaded = new HashSet<>();
    public static void add(ControllerBlockEntity be) { loaded.add(be); }
    public static void remove(ControllerBlockEntity be) { loaded.remove(be); }
    public static void levelUnload(LevelEvent.Unload e) { loaded.removeIf(be -> be.getLevel() == e.getLevel()); }
    public static void checkSpawn(MobSpawnEvent.PositionCheck e) {
        if (e.getSpawnType() != EntitySpawnReason.NATURAL || e.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        ServerLevel level=e.getLevel().getLevel();
        for (ControllerBlockEntity be : loaded) {
            if (be.getLevel()!=level || be.isRemoved() || !be.enabled || be.protectedUntil<=level.getGameTime()) continue;
            if (Math.abs(e.getX()-be.getBlockPos().getX()-0.5)<=be.protectedRadius && Math.abs(e.getY()-be.getBlockPos().getY()-0.5)<=be.protectedRadius && Math.abs(e.getZ()-be.getBlockPos().getZ()-0.5)<=be.protectedRadius) {
                e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL); return;
            }
        }
    }
}
