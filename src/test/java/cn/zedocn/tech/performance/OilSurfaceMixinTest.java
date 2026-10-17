package cn.zedocn.tech.performance;

import com.hbm.world.feature.OilSurfaceScarring;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class OilSurfaceMixinTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void actualPatchedMethodUsesNonAirHeightmapForEveryRandomColumn() {
        for (BlockState surface : new BlockState[] { Blocks.STONE.defaultBlockState(),
                Blocks.WATER.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState() }) {
            var heightQueries = new AtomicInteger(); var blockQueries = new AtomicInteger();
            var level = fixture(surface, 70, heightQueries, blockQueries);
            OilSurfaceScarring.generateOilSpot(level, RandomSource.create(20261017), -660, 483, 75, 10);
            assertEquals(10, heightQueries.get(), surface.toString());
            assertTrue(blockQueries.get() <= 100, "must not rescan the air above " + surface);
        }
    }

    @Test void emptyColumnsTerminateAtTheOriginalBelowMinimumBoundary() {
        var heights = new AtomicInteger(); var blocks = new AtomicInteger();
        OilSurfaceScarring.generateOilSpot(fixture(Blocks.AIR.defaultBlockState(), -65, heights, blocks),
                RandomSource.create(5), 0, 0, 0, 10);
        assertEquals(10, heights.get());
        assertEquals(80, blocks.get());
    }

    private static LevelAccessor fixture(BlockState surface, int surfaceY,
            AtomicInteger heightQueries, AtomicInteger blockQueries) {
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                new Class<?>[] { LevelAccessor.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getMaxY" -> 319;
                        case "getMinY" -> -64;
                        case "getHeight" -> {
                            assertEquals(Heightmap.Types.WORLD_SURFACE, args[0]);
                            heightQueries.incrementAndGet();
                            yield surfaceY + 1;
                        }
                        case "getBlockState" -> {
                            var pos = (BlockPos) args[0];
                            assertTrue(pos.getY() <= surfaceY + 1, "unexpected scan at y=" + pos.getY());
                            blockQueries.incrementAndGet();
                            yield pos.getY() == surfaceY ? surface : Blocks.AIR.defaultBlockState();
                        }
                        case "setBlock" -> true;
                        case "toString" -> "oil surface fixture";
                        default -> throw new AssertionError("unexpected world access " + method.getName());
                    };
                });
    }
}
