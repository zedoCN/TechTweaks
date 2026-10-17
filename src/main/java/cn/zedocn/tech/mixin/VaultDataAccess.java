package cn.zedocn.tech.mixin;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(VaultServerData.class)
public interface VaultDataAccess {
    @Invoker("getItemsToEject") List<ItemStack> techItems();
    @Invoker("getNextItemToEject") ItemStack techNextItem();
    @Invoker("pauseStateUpdatingUntil") void techPause(long time);
}
