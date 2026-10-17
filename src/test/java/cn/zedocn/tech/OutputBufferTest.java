package cn.zedocn.tech;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.Transaction;

class OutputBufferTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var components=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build();
        for(var item:java.util.List.of(Items.DIAMOND,Items.EMERALD,Items.TRIAL_KEY)) {
            if(!item.builtInRegistryHolder().areComponentsBound()) item.builtInRegistryHolder().bindComponents(components);
        }
    }
    @Test void backpressureDoesNotTouchInputOrExistingOutput() {
        var inventory=new SimpleContainer(3);
        inventory.setItem(0,new ItemStack(Items.TRIAL_KEY,3));
        inventory.setItem(1,new ItemStack(Items.DIAMOND,64));
        inventory.setItem(2,new ItemStack(Items.EMERALD,64));
        var incoming=new ItemStack(Items.DIAMOND,12);
        assertEquals(12,OutputBuffer.transfer(inventory,incoming,1,false).getCount());
        assertEquals(12,OutputBuffer.transfer(inventory,incoming,1,true).getCount());
        assertEquals(3,inventory.getItem(0).getCount()); assertEquals(64,inventory.getItem(1).getCount()); assertEquals(12,incoming.getCount());
    }
    @Test void probeLeavesInventoryUntouchedAndCommitConservesItems() {
        var inventory=new SimpleContainer(4);
        inventory.setItem(0,new ItemStack(Items.TRIAL_KEY)); inventory.setItem(1,new ItemStack(Items.DIAMOND,60));
        var incoming=new ItemStack(Items.DIAMOND,70);
        assertTrue(OutputBuffer.transfer(inventory,incoming,1,false).isEmpty());
        assertEquals(60,inventory.getItem(1).getCount()); assertTrue(inventory.getItem(2).isEmpty());
        assertTrue(OutputBuffer.transfer(inventory,incoming,1,true).isEmpty());
        assertEquals(130,inventory.getItem(1).getCount()+inventory.getItem(2).getCount()+inventory.getItem(3).getCount()); assertEquals(70,incoming.getCount());
    }
    @Test void extractionRollbackRestoresOriginalCount() {
        var inventory=new SimpleContainer(1); inventory.setItem(0,new ItemStack(Items.DIAMOND,15));
        var handler=VanillaContainerWrapper.of(inventory);
        try(var tx=Transaction.openRoot()) { assertEquals(4,handler.extract(0,ItemResource.of(Items.DIAMOND),4,tx)); }
        assertEquals(15,inventory.getItem(0).getCount());
        try(var tx=Transaction.openRoot()) { assertEquals(4,handler.extract(0,ItemResource.of(Items.DIAMOND),4,tx)); tx.commit(); }
        assertEquals(11,inventory.getItem(0).getCount());
    }
}
