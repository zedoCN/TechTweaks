package cn.zedocn.tech.mixin;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.vault.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(VaultBlockEntity.Server.class)
public interface VaultServerAccess {
    @Invoker("resolveItemsToEject") static List<ItemStack> techResolve(ServerLevel level,VaultConfig config,BlockPos pos,Player player,ItemInstance key) { throw new AssertionError(); }
    @Invoker("unlock") static void techUnlock(ServerLevel level,BlockState state,BlockPos pos,VaultConfig config,VaultServerData data,VaultSharedData shared,List<ItemStack> items) { throw new AssertionError(); }
}
