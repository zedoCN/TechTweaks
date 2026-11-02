package cn.zedocn.tech.integration;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.inventory.fluid.tank.FluidTankNTM;
import com.hbm.handler.ArmorModHandler;
import cn.zedocn.tech.mixin.FluidTankNativeAccess;
import net.minecraft.core.NonNullList;
import com.hbm.items.machine.IFluidContainerItem;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Keeps the native drain/fill lanes and moves in-place fillable items only when complete. */
public final class TankAutomation {
    public static final int[] SLOTS = {2, 3, 4, 5};

    private TankAutomation() {}

    public static boolean accepts(FluidTankNTM tank, int slot, ItemStack stack) {
        if (stack.isEmpty() || tank.getPressure() != 0
                || (slot != 2 && slot != 4)) return false;
        if (inPlace(stack)) {
            return tank.getDeclaredFluid() == null ? slot == 4
                    : nativeInPlaceTransfer(tank, stack, slot == 4);
        }
        var inventory = new SimpleContainer(stack.copyWithCount(1));
        var access = ItemAccess.forHandlerIndexStrict(VanillaContainerWrapper.of(inventory), 0);
        var fluids = access.getCapability(Capabilities.Fluid.ITEM);
        if (fluids == null) return false;
        // canPlaceItem is called from inside Pipez/AE2 transactions. HBM's
        // containerRoom/containerContent helpers open a root transaction and throw
        // there; use an aborting nested probe on a private copy instead.
        try (var probe = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            if (slot == 4) {
                if (tank.getDeclaredFluid() == null) {
                    // Empty containers may queue before the first fluid arrives.
                    for (int i = 0; i < fluids.size(); i++) if (!fluids.getResource(i).isEmpty()) return false;
                    return fluids.size() > 0;
                }
                return fluids.insert(FluidResource.of(tank.getFluid()), Integer.MAX_VALUE, probe) > 0;
            }
            for (int i = 0; i < fluids.size(); i++) {
                var resource = fluids.getResource(i);
                if (!resource.isEmpty() && (tank.getDeclaredFluid() == null || tank.accepts(resource.getFluid()))
                        && fluids.extract(i, resource, Integer.MAX_VALUE, probe) > 0) return true;
            }
            return false;
        }
    }

    public static boolean untypedInput(FluidTankNTM tank, Fluid fluid, int pressure) {
        return fluid != null && fluid != Fluids.EMPTY && tank.getDeclaredFluid() == null
                && tank.getFill() == 0 && pressure == tank.getPressure();
    }

    /** Only committed inventory is examined, on the native server tick. */
    public static void primeFromContainer(BlockEntityMachineBase machine, FluidTankNTM tank) {
        if (tank.getDeclaredFluid() != null || tank.getFill() != 0 || tank.getPressure() != 0) return;
        var stack = machine.getItem(2);
        if (stack.isEmpty()) return;
        var copy = new SimpleContainer(stack.copyWithCount(1));
        var access = ItemAccess.forHandlerIndexStrict(VanillaContainerWrapper.of(copy), 0);
        var fluids = access.getCapability(Capabilities.Fluid.ITEM);
        if (fluids == null) return;
        for (int i = 0; i < fluids.size(); i++) {
            var resource = fluids.getResource(i);
            if (!resource.isEmpty() && fluids.getAmountAsLong(i) > 0) {
                tank.setTankType(resource.getFluid());
                machine.setChanged();
                return;
            }
        }
    }

    private static boolean inPlace(ItemStack stack) {
        return ArmorModHandler.hasMods(stack)
                || stack.getItem() instanceof IFluidContainerItem container && container.machineFillable();
    }

    /** Run NTM's in-place operations on detached copies; never simulate on live armor. */
    private static boolean nativeInPlaceTransfer(FluidTankNTM tank, ItemStack stack, boolean filling) {
        if (tank.getFluid() == null) return false;
        var scratch = new FluidTankNTM(Integer.MAX_VALUE).withPressure(tank.getPressure());
        scratch.setTankTypeByIdentifier(tank.getFluid());
        scratch.setFill(filling ? Integer.MAX_VALUE : 0);
        int before = scratch.getFill();
        var copy = stack.copyWithCount(1);
        if (copy.getItem() instanceof IFluidContainerItem container && container.machineFillable()) {
            // This native branch handles the item itself plus any installed mods.
            var slots = NonNullList.withSize(2, ItemStack.EMPTY);
            slots.set(0, copy);
            if (filling) scratch.unloadTank(0, 1, slots);
            else scratch.loadTank(0, 1, slots);
        } else {
            // Native armor handling, without probing an unrelated fluid capability
            // that may open a root transaction inside the caller's transaction.
            ((FluidTankNativeAccess)scratch).tech$serveFillableMods(copy, !filling);
        }
        return scratch.getFill() != before;
    }

    public static void finish(BlockEntityMachineBase machine, FluidTankNTM tank) {
        if (tank.getDeclaredFluid() == null || tank.getPressure() != 0) return;
        finishLane(machine, tank, 2, 3, false);
        finishLane(machine, tank, 4, 5, true);
    }

    private static void finishLane(BlockEntityMachineBase machine, FluidTankNTM tank,
                                   int input, int output, boolean filling) {
        ItemStack stack = machine.getItem(input);
        if (stack.isEmpty() || !inPlace(stack) || nativeInPlaceTransfer(tank, stack, filling)) return;
        ItemStack destination = machine.getItem(output);
        if (!destination.isEmpty() && !ItemStack.isSameItemSameComponents(stack, destination)) return;
        int room = Math.min(machine.getMaxStackSize(), stack.getMaxStackSize()) - destination.getCount();
        if (room < stack.getCount()) return;
        if (destination.isEmpty()) machine.setItem(output, stack);
        else destination.grow(stack.getCount());
        machine.setItem(input, ItemStack.EMPTY);
        machine.setChanged();
    }
}
