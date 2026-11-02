package cn.zedocn.tech.server.network;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;

/** Fake players have no client to consume EmbeddedChannel's outbound queue. */
public final class ClientlessChannel extends EmbeddedChannel {
    @Override
    protected void handleOutboundMessage(Object message) {
        // EmbeddedChannel.doWrite retains before this callback, then removes and
        // releases the original write. Consume only the callback's retained share.
        ReferenceCountUtil.release(message);
    }
}
