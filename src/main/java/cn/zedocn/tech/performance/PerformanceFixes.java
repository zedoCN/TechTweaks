package cn.zedocn.tech.performance;

import com.hbm.blocks.ModBlocks;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class PerformanceFixes {
    private static long pipeBatches;
    private static long pipeItems;
    private static long rejectedProbesSkipped;
    private static long surfaceColumns;
    private static long staleLeaves;

    private PerformanceFixes() {}

    static void pipeBatch(int items) { pipeBatches++; pipeItems += items; }

    static void rejectedProbeSkipped() { rejectedProbesSkipped++; }

    public static int surfaceY(LevelAccessor level, int x, int z) {
        surfaceColumns++;
        // WORLD_SURFACE uses the same non-air test as the original vertical scan,
        // including fluids, foliage and roofs. It returns the position ABOVE the surface.
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    public static boolean discardStaleLeaves(FallingBlockEntity entity) {
        if (!(entity.level() instanceof ServerLevel) || !entity.isAlive()
                || entity.time <= 600 || entity.dropItem || entity.blockData != null
                || !entity.onGround() || entity.getDeltaMovement().lengthSqr() > 1.0E-8
                || !entity.getBlockState().is(ModBlocks.LEAVES_LAYER.get())
                || entity.level().getBlockState(entity.blockPosition()).is(Blocks.MOVING_PISTON)) return false;
        // Normal placement has already run. Only an old, stationary, payload-free
        // HBM leaf that survived that attempt is removed; other falling blocks are untouched.
        entity.discard();
        staleLeaves++;
        return true;
    }

    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tech")
                .then(Commands.literal("performance")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "TECH performance: pipe_batches=" + pipeBatches + " pipe_items=" + pipeItems
                                    + " pipe_rejects_skipped=" + rejectedProbesSkipped + " surface_columns=" + surfaceColumns + " stale_leaves=" + staleLeaves), false);
                            return 1;
                        })));
    }
}
