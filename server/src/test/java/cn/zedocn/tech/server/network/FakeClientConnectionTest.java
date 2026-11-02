package cn.zedocn.tech.server.network;

import dev.anvilcraft.rg.sd.entity.FakeClientConnection;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.protocol.PacketFlow;
import com.hbm.packet.threading.ThreadedPayload;
import com.hbm.packet.toclient.PlayerAppearancePayload;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FakeClientConnectionTest {
    private EmbeddedChannel fake() {
        var connection = new FakeClientConnection(PacketFlow.SERVERBOUND);
        return assertInstanceOf(ClientlessChannel.class, connection.channel());
    }

    @Test void actualFakeConnectionReleasesAnOwnedBufferAndCompletesItsPromise() {
        var channel = fake();
        var buffer = Unpooled.directBuffer(32).writeInt(42);
        try {
            var sent = channel.writeAndFlush(buffer);
            assertTrue(sent.isSuccess());
            assertEquals(0, buffer.refCnt());
            assertTrue(channel.outboundMessages().isEmpty());
            assertTrue(channel.isOpen());
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test void retainedDuplicatesReleaseOnlyTheTransferredShare() {
        var channel = fake();
        var shared = PooledByteBufAllocator.DEFAULT.directBuffer(32).writeInt(42);
        try {
            for (int i = 0; i < 100_000; i++) {
                channel.write(shared.retainedDuplicate(), channel.voidPromise());
                if ((i & 255) == 255) channel.flush();
            }
            channel.flush();
            assertTrue(channel.outboundMessages().isEmpty());
            assertEquals(1, shared.refCnt(), "the sender still owns the shared payload");
            assertEquals(42, shared.getInt(0));
        } finally { channel.finishAndReleaseAll(); shared.release(); }
        assertEquals(0, shared.refCnt());
    }

    @Test void holdersAndNonReferenceCountedPacketsAreConsumed() {
        var channel = fake();
        var holder = new DefaultByteBufHolder(Unpooled.buffer(8).writeLong(123));
        try {
            assertTrue(channel.writeAndFlush(holder).isSuccess());
            assertEquals(0, holder.refCnt());
            assertTrue(channel.writeAndFlush(new Object()).isSuccess());
            assertTrue(channel.outboundMessages().isEmpty());
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test void closingWithUnflushedWritesReleasesThem() {
        var channel = fake();
        var buffer = Unpooled.directBuffer(32);
        channel.write(buffer);
        channel.finishAndReleaseAll();
        assertEquals(0, buffer.refCnt());
        assertTrue(channel.outboundMessages().isEmpty());
    }

    @Test void ordinaryEmbeddedChannelsStillRetainMessagesForTheirConsumer() {
        var channel = new EmbeddedChannel();
        var buffer = Unpooled.buffer(8).writeInt(42);
        try {
            assertTrue(channel.writeAndFlush(buffer).isSuccess());
            assertEquals(1, buffer.refCnt());
            assertSame(buffer, channel.readOutbound());
            assertEquals(42, buffer.readInt());
            buffer.release();
        } finally { channel.finishAndReleaseAll(); }
    }
    private Runnable batch(EmbeddedChannel channel, ThreadedPayload payload) throws Exception {
        var connection = mock(Connection.class);
        when(connection.channel()).thenReturn(channel);
        var listener = mock(ServerGamePacketListenerImpl.class);
        when(listener.getConnection()).thenReturn(connection);
        var player = mock(ServerPlayer.class);
        player.connection = listener;
        var type = Class.forName("com.hbm.packet.OutboundBatch");
        var take = type.getDeclaredMethod("take", ServerPlayer.class);
        take.setAccessible(true);
        var batch = take.invoke(null, player);
        var add = type.getDeclaredMethod("add", ThreadedPayload.class);
        add.setAccessible(true);
        add.invoke(batch, payload);
        return (Runnable) batch;
    }

    @Test void actualHbmBatchesCannotAccumulateOnFakeConnections() throws Exception {
        var channel = fake();
        try {
            for (int i = 0; i < 10_000; i++) {
                var payload = new PlayerAppearancePayload(123, (byte) 0);
                var compiled = payload.getCompiledBuffer();
                var batch = batch(channel, payload);
                payload.release();
                batch.run();
                assertEquals(0, compiled.refCnt(), "batch " + i);
            }
            assertTrue(channel.outboundMessages().isEmpty());
            assertTrue(channel.isOpen());
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test void fakeRecipientDoesNotConsumeAnotherRecipientsSharedPayload() throws Exception {
        var fake = fake();
        var consumer = new EmbeddedChannel();
        try {
            var payload = new PlayerAppearancePayload(123, (byte) 0);
            var compiled = payload.getCompiledBuffer();
            var fakeBatch = batch(fake, payload);
            var otherBatch = batch(consumer, payload);
            payload.release();
            fakeBatch.run();
            assertEquals(1, compiled.refCnt());
            assertTrue(fake.outboundMessages().isEmpty());
            otherBatch.run();
            assertEquals(1, consumer.outboundMessages().size());
            var received = (io.netty.buffer.ByteBuf) consumer.readOutbound();
            assertTrue(received.isReadable());
            received.release();
            assertEquals(0, compiled.refCnt());
        } finally { fake.finishAndReleaseAll(); consumer.finishAndReleaseAll(); }
    }

}
