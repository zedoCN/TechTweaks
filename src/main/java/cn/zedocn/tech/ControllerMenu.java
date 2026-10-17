package cn.zedocn.tech;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class ControllerMenu extends AbstractContainerMenu {
    public final Container container;
    public final ContainerData data;
    public ControllerMenu(int id, Inventory inv) { this(id, inv, new SimpleContainer(9), new SimpleContainerData(13)); }
    public ControllerMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(TechTweaks.MENU.get(),id); this.container=container; this.data=data;
        for (int i=0;i<9;i++) {
            final int slot=i;
            addSlot(new Slot(container,i,8+i*18,100) { @Override public boolean mayPlace(ItemStack stack) { return slot == 0 && (container instanceof ControllerBlockEntity ? container.canPlaceItem(slot,stack) : data.get(0)==0 ? stack.is(net.minecraft.world.item.Items.OMINOUS_BOTTLE) : data.get(0)==1 && (stack.is(net.minecraft.world.item.Items.TRIAL_KEY) || stack.is(net.minecraft.world.item.Items.OMINOUS_TRIAL_KEY))); } });
        }
        for (int y=0;y<3;y++) for (int x=0;x<9;x++) addSlot(new Slot(inv,x+y*9+9,8+x*18,138+y*18));
        for (int x=0;x<9;x++) addSlot(new Slot(inv,x,8+x*18,196));
        addDataSlots(data);
    }
    @Override public boolean stillValid(Player p) { return container.stillValid(p); }
    @Override public boolean clickMenuButton(Player p,int id) {
        if (id<0 || id>7 || !stillValid(p) || !(container instanceof ControllerBlockEntity be)) return false;
        be.action(id); return true;
    }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if (index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack current=slot.getItem(), original=current.copy();
        if (index<9) { if (!moveItemStackTo(current,9,slots.size(),true)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(current,0,1,false)) return ItemStack.EMPTY;
        if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(p,current); return original;
    }
}
