package cn.zedocn.tech;

import com.hbm.entity.projectile.EntityMeteor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MeteorGuidance {
    public static final int RANGE = 256;

    private MeteorGuidance() {}

    /** Called only after HBM has already rolled and constructed a natural meteor. */
    public static Entity guide(Entity entity) {
        if (!(entity instanceof EntityMeteor meteor) || !(meteor.level() instanceof ServerLevel level)) return entity;
        BlockPos rod = nearestRod(level, meteor.getX(), meteor.getZ());
        if (rod == null) return entity;

        // HBM's falling/impact logic, safe flag, random source and ore generation stay intact.
        // Clear only the horizontal drift so the existing meteor falls onto this rod.
        meteor.snapTo(rod.getX() + 0.5, Math.max(meteor.getY(), rod.getY() + 33.0),
                rod.getZ() + 0.5, meteor.getYRot(), meteor.getXRot());
        meteor.setDeltaMovement(0.0, meteor.getDeltaMovement().y, 0.0);
        return entity;
    }

    static BlockPos nearestRod(ServerLevel level, double x, double z) {
        BlockPos nearest = null;
        double nearestDistance = RANGE * RANGE;
        int minX = Mth.floor(x - RANGE) >> 4, maxX = Mth.floor(x + RANGE) >> 4;
        int minZ = Mth.floor(z - RANGE) >> 4, maxZ = Mth.floor(z + RANGE) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof MeteorGuideRodBlockEntity) || be.isRemoved()) continue;
                    BlockPos pos = be.getBlockPos();
                    double distance = horizontalDistanceSquared(pos, x, z);
                    if (distance > nearestDistance || !isActive(chunk.getBlockState(pos))) continue;
                    // WORLD_SURFACE includes transparent roofs and fluids: the rod must be
                    // the topmost non-air block, rather than merely receiving skylight.
                    if (chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) != pos.getY()) continue;
                    if (distance == nearestDistance && nearest != null && pos.compareTo(nearest) >= 0) continue;
                    nearest = pos;
                    nearestDistance = distance;
                }
            }
        }
        return nearest;
    }

    static boolean isActive(BlockState state) {
        return state.getBlock() instanceof MeteorGuideRodBlock
                && state.getValue(LightningRodBlock.FACING) == Direction.UP
                && !state.getValue(LightningRodBlock.WATERLOGGED);
    }

    static double horizontalDistanceSquared(BlockPos pos, double x, double z) {
        double dx = pos.getX() + 0.5 - x, dz = pos.getZ() + 0.5 - z;
        return dx * dx + dz * dz;
    }
}
