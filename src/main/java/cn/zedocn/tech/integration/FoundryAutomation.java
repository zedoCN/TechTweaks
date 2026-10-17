package cn.zedocn.tech.integration;

import com.hbm.blockentity.machine.BlockEntityFoundryCastingBase;
import com.hbm.capability.NtmCapabilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

public final class FoundryAutomation {
    public static void register(RegisterCapabilitiesEvent event) {
        Block[] hbm = BuiltInRegistries.BLOCK.stream().filter(b -> BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals("hbm")).toArray(Block[]::new);
        // NTM's item provider accepts MachineBase only. Foundry casting machines use WorldlyContainer instead.
        // Ask NTM for the port owner first so sealed faces and non-item multiblock cells stay sealed.
        event.registerBlock(Capabilities.Item.BLOCK, (level,pos,state,be,side) -> {
            if (side != null && NtmCapabilities.itemOwnerAt(level,pos,state,side) instanceof BlockEntityFoundryCastingBase casting)
                return new WorldlyContainerWrapper(casting,side);
            return null;
        },hbm);
    }
}
