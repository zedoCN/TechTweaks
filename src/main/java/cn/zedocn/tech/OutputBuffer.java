package cn.zedocn.tech;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
public final class OutputBuffer {
    public static ItemStack transfer(Container inventory,ItemStack incoming,int firstSlot,boolean apply) {
        ItemStack remaining=incoming.copy();
        for(int i=firstSlot;i<inventory.getContainerSize() && !remaining.isEmpty();i++) {
            ItemStack current=inventory.getItem(i);
            if(!current.isEmpty() && !ItemStack.isSameItemSameComponents(current,remaining)) continue;
            int limit=Math.min(remaining.getMaxStackSize(),inventory.getMaxStackSize(remaining));
            int moved=Math.min(remaining.getCount(),Math.max(0,limit-current.getCount()));
            if(apply && moved>0) inventory.setItem(i,remaining.copyWithCount(current.getCount()+moved));
            remaining.shrink(moved);
        }
        return remaining;
    }
}
