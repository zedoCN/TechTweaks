package cn.zedocn.tech.integration;

import com.hbm.blockentity.machine.BlockEntityFoundryCastingBase;
import com.hbm.capability.NtmCapabilities;
import com.hbm.api.fluidmk2.IFluidHandlerMK2;
import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blockentity.machine.storage.BlockEntityMachineFluidTank;
import com.hbm.blockentity.machine.storage.BlockEntityMachineOrbus;
import com.hbm.capability.MachineItemHandler;
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
        // These tanks declare fluid cells but not item cells. The native provider
        // consequently exposes only the core; resolve their existing open fluid
        // faces instead, so real hoppers and AE buses can reach the container lanes.
        event.registerBlock(Capabilities.Item.BLOCK, (level,pos,state,be,side) -> {
            if (side == null) return null;
            var owner = NtmCapabilities.fullSurfaceCap(level, pos, state, side,
                    IFluidHandlerMK2.class, NtmCapabilities.CapRole.FLUID_IN);
            if (owner == null) owner = NtmCapabilities.fullSurfaceCap(level, pos, state, side,
                    IFluidHandlerMK2.class, NtmCapabilities.CapRole.FLUID_OUT);
            if (owner instanceof BlockEntityMachineFluidTank || owner instanceof BlockEntityMachineOrbus)
                return MachineItemHandler.of((BlockEntityMachineBase)owner, side);
            return null;
        },hbm);
    }
}
