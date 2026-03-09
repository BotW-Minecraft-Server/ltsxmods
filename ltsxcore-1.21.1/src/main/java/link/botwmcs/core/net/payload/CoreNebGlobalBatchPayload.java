package link.botwmcs.core.net.payload;

import io.netty.buffer.Unpooled;
import link.botwmcs.core.net.neb.global.CoreNebGlobalPacketUtil;
import link.botwmcs.core.net.stat.CoreNebPacketFlowStat;
import link.botwmcs.core.net.stat.CoreNebTrafficStat;
import link.botwmcs.core.util.CoreIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global NEB transport payload used by Connection mixin mode.
 */
public final class CoreNebGlobalBatchPayload implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreNebGlobalBatchPayload.class);

    public static final Type<CoreNebGlobalBatchPayload> TYPE = new Type<>(CoreIds.id("core_neb_global_batch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreNebGlobalBatchPayload> STREAM_CODEC = StreamCodec.ofMember(
            CoreNebGlobalBatchPayload::encode,
            CoreNebGlobalBatchPayload::new
    );

    private final byte[] data;
    private final int rawSizeHint;

    public CoreNebGlobalBatchPayload(byte[] data, int rawSizeHint) {
        this.data = data;
        this.rawSizeHint = Math.max(0, rawSizeHint);
    }

    private CoreNebGlobalBatchPayload(RegistryFriendlyByteBuf buf) {
        this.data = buf.readByteArray();
        this.rawSizeHint = 0;
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeByteArray(this.data);
    }

    public byte[] data() {
        return data;
    }

    public int rawSizeHint() {
        return rawSizeHint;
    }

    public void handle(IPayloadContext context) {
        FriendlyByteBuf dataBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(this.data));
        FriendlyByteBuf rawBuf = dataBuf;
        try {
            boolean compressed = dataBuf.readBoolean();
            if (compressed) {
                int rawSize = dataBuf.readVarInt();
                rawBuf = new FriendlyByteBuf(link.botwmcs.core.net.neb.CoreNebZstdHelper.decompress(context.connection(), dataBuf, rawSize));
            }

            CoreNebTrafficStat.globalInRaw(rawBuf.readableBytes());

            @SuppressWarnings("unchecked")
            var codec = (net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Packet<?>>) context.connection().getInboundProtocol().codec();
            while (rawBuf.readableBytes() > 0) {
                int size = rawBuf.readVarInt();
                io.netty.buffer.ByteBuf packetSlice = rawBuf.readRetainedSlice(size);
                try {
                    Packet<?> packet = codec.decode(packetSlice);
                    CoreNebPacketFlowStat.in(CoreNebGlobalPacketUtil.getTrueType(packet), size);
                    try {
                        context.handle(packet);
                    } catch (RunningOnDifferentThreadException ignored) {
                        // Vanilla packet handlers throw this after re-scheduling onto the main thread.
                    }
                } finally {
                    packetSlice.release();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to decode global NEB batch payload", e);
        } finally {
            rawBuf.release();
            if (rawBuf != dataBuf) {
                dataBuf.release();
            }
        }
    }

    @Override
    public Type<CoreNebGlobalBatchPayload> type() {
        return TYPE;
    }
}
