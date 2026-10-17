package cn.zedocn.tech;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import com.hbm.blockentity.machine.BlockEntityFoundryCastingBase;
import com.hbm.blockentity.BlockEntityMachineBase;
import net.minecraft.world.WorldlyContainer;
class FoundryPortTest {
    @Test void castingMachinesReallyUseTheContainerPathMissingFromNtmProvider() {
        assertTrue(WorldlyContainer.class.isAssignableFrom(BlockEntityFoundryCastingBase.class));
        assertFalse(BlockEntityMachineBase.class.isAssignableFrom(BlockEntityFoundryCastingBase.class));
    }
    @Test void vaultAccessorsLoadWithTheInstalledNtmMixins() throws Exception {
        Class.forName("net.minecraft.world.level.block.entity.vault.VaultBlockEntity$Server");
        var data=new net.minecraft.world.level.block.entity.vault.VaultServerData();
        var access=assertInstanceOf(cn.zedocn.tech.mixin.VaultDataAccess.class,data);
        assertTrue(access.techItems().isEmpty()); access.techPause(200);
    }
}
