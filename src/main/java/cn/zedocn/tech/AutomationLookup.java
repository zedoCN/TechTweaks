package cn.zedocn.tech;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
public final class AutomationLookup {
    public static ControllerBlockEntity controller(Level level, BlockPos target, ControllerKind kind) {
        for (Direction d : Direction.values()) {
            BlockPos pos = target.relative(d);
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof ControllerBlockEntity be && be.kind()==kind && be.targetPos().equals(target)) return be;
        }
        return null;
    }
}
