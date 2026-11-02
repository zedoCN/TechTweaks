package cn.zedocn.tech.server.mixin;

import cn.zedocn.tech.server.network.ClientlessChannel;
import dev.anvilcraft.rg.sd.entity.FakeClientConnection;
import io.netty.channel.embedded.EmbeddedChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FakeClientConnection.class, remap = false)
public abstract class FakeClientConnectionMixin {
    @Redirect(method = "<init>", at = @At(value = "NEW",
            target = "io/netty/channel/embedded/EmbeddedChannel"))
    private EmbeddedChannel tech$clientlessChannel() {
        return new ClientlessChannel();
    }
}
