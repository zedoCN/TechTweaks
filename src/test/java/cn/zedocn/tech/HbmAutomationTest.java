package cn.zedocn.tech;

import com.hbm.blockentity.machine.fusion.BlockEntityFusionPlasmaForge;
import com.hbm.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HbmAutomationTest {
    @Test void expandingTheForgePortDoesNotExpandItsExtractionPermission() {
        var block = ModBlocks.FUSION_PLASMA_FORGE.get();
        var machine = new BlockEntityFusionPlasmaForge(BlockPos.ZERO, block.defaultBlockState());
        var port = machine.itemAccess(BlockPos.ZERO, Direction.DOWN);
        var stack = ItemStack.EMPTY;
        for (int slot = 3; slot < 15; slot++) assertFalse(port.canExtract(slot, stack), "input " + slot);
        assertTrue(port.canExtract(15, stack));
        assertFalse(port.canInsert(0, stack));
        assertFalse(port.canInsert(1, stack));
        assertFalse(port.canInsert(15, stack));
        assertSame(port, machine.itemAccess(BlockPos.ZERO, Direction.DOWN));
    }
}
