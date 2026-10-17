package cn.zedocn.tech;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeteorGuidanceTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void onlyUprightDryGuideRodIsEligible() {
        var rod = TechTweaks.METEOR_ROD.get();
        var state = rod.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.UP);
        assertTrue(MeteorGuidance.isActive(state));
        assertFalse(MeteorGuidance.isActive(state.setValue(LightningRodBlock.WATERLOGGED, true)));
        for (Direction facing : Direction.values()) {
            if (facing != Direction.UP) assertFalse(MeteorGuidance.isActive(state.setValue(LightningRodBlock.FACING, facing)));
        }
        assertFalse(MeteorGuidance.isActive(Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState()));
    }

    @Test void hbmNaturalSpawnHooksApplyToThePinnedJar() throws ClassNotFoundException {
        // Loading the actual target through NeoForge's test loader applies its mixins.
        // Required injections fail here if HBM changes either method/call descriptor.
        Class<?> target = Class.forName("com.hbm.handler.BossSpawnHandler", false, getClass().getClassLoader());
        var names = Arrays.stream(target.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
        assertTrue(names.stream().anyMatch(name -> name.contains("tech$naturalMeteor")), names.toString());
        assertTrue(names.stream().anyMatch(name -> name.contains("tech$guideNaturalMeteor")), names.toString());
    }
}
