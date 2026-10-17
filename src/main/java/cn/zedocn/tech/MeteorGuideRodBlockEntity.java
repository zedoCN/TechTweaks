package cn.zedocn.tech;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stored in the chunk's own entity map, so unloading automatically removes the candidate. */
public final class MeteorGuideRodBlockEntity extends BlockEntity {
    public MeteorGuideRodBlockEntity(BlockPos pos, BlockState state) {
        super(TechTweaks.METEOR_ROD_ENTITY.get(), pos, state);
    }
}
