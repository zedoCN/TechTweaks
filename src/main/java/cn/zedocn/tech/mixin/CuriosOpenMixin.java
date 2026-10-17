package cn.zedocn.tech.mixin;
import cn.zedocn.tech.integration.CuriosSupport;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;
import top.theillusivec4.curios.common.network.server.CuriosServerPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CuriosServerPayloadHandler.class,remap=false)
public abstract class CuriosOpenMixin {
    @Inject(method="handleOpenCurios",at=@At("HEAD"))
    private void techOpen(CPacketOpenCurios data,IPayloadContext context,CallbackInfo ci) {
        context.enqueueWork(() -> { if(context.player() instanceof ServerPlayer player) CuriosSupport.logOpen(player); });
    }
}
