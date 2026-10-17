package cn.zedocn.tech;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A vanilla rod shape with a stateless marker entity; no ticking or chunk tickets. */
public final class MeteorGuideRodBlock extends LightningRodBlock implements EntityBlock {
    public static final MapCodec<MeteorGuideRodBlock> CODEC = simpleCodec(MeteorGuideRodBlock::new);

    public MeteorGuideRodBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override public MapCodec<MeteorGuideRodBlock> codec() { return CODEC; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MeteorGuideRodBlockEntity(pos, state);
    }
}
