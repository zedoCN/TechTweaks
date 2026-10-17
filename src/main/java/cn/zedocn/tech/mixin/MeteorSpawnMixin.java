package cn.zedocn.tech.mixin;

import cn.zedocn.tech.MeteorGuidance;
import com.hbm.handler.BossSpawnHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BossSpawnHandler.class, remap = false)
public abstract class MeteorSpawnMixin {
    // HBM runs meteorUpdate on the server thread. Scope this flag to its existing
    // spawn call so the meteor remote, commands and chunk worldgen are unaffected.
    @Unique private static boolean tech$naturalMeteor;

    @Redirect(method = "meteorUpdate", at = @At(value = "INVOKE",
            target = "Lcom/hbm/handler/BossSpawnHandler;spawnMeteorAtPlayer(Lnet/minecraft/world/entity/player/Player;Z)V"))
    private static void tech$naturalMeteor(Player player, boolean safe) {
        boolean previous = tech$naturalMeteor;
        tech$naturalMeteor = true;
        try {
            BossSpawnHandler.spawnMeteorAtPlayer(player, safe);
        } finally {
            tech$naturalMeteor = previous;
        }
    }

    @ModifyArg(method = "spawnMeteorAtPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), index = 0)
    private static Entity tech$guideNaturalMeteor(Entity meteor) {
        return tech$naturalMeteor ? MeteorGuidance.guide(meteor) : meteor;
    }
}
