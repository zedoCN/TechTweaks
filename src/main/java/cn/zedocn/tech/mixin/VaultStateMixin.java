package cn.zedocn.tech.mixin;
import cn.zedocn.tech.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.vault.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(VaultState.class)
public abstract class VaultStateMixin {
    @Inject(method="tickAndGetNext",at=@At("HEAD"),cancellable=true)
    private void techBackpressure(ServerLevel level,BlockPos pos,VaultConfig config,VaultServerData data,VaultSharedData shared,CallbackInfoReturnable<VaultState> ci) {
        if ((Object)this!=VaultState.EJECTING || ((VaultDataAccess)data).techItems().isEmpty()) return;
        var be=AutomationLookup.controller(level,pos,ControllerKind.VAULT);
        if (be!=null && !be.canFit(((VaultDataAccess)data).techNextItem())) {
            ((VaultDataAccess)data).techPause(level.getGameTime()+20); be.status=7; ci.setReturnValue(VaultState.EJECTING);
        }
    }
    @Redirect(method="ejectResultItem",at=@At(value="INVOKE",target="Lnet/minecraft/core/dispenser/DefaultDispenseItemBehavior;spawnItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;Lnet/minecraft/core/Position;)V"))
    private static void techOutput(net.minecraft.world.level.Level level,ItemStack stack,int speed,Direction direction,net.minecraft.core.Position location,ServerLevel server,BlockPos pos,ItemStack item,float progress) {
        var be=AutomationLookup.controller(level,pos,ControllerKind.VAULT);
        ItemStack remaining=be==null?stack:be.receive(stack);
        if (!remaining.isEmpty()) net.minecraft.core.dispenser.DefaultDispenseItemBehavior.spawnItem(level,remaining,speed,direction,location);
    }
}
