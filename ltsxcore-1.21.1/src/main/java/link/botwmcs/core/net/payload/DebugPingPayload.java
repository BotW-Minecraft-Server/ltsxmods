package link.botwmcs.core.net.payload;

import link.botwmcs.core.net.CorePacketPayload;
import link.botwmcs.core.net.CorePayloadType;
import link.botwmcs.core.util.CoreIds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Example payload used to verify core networking and server-side handling flow.
 */
public record DebugPingPayload(long clientTime) implements CorePacketPayload {
    public static final CorePayloadType<DebugPingPayload> TYPE = new CorePayloadType<>(CoreIds.id("debug_ping"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugPingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            DebugPingPayload::clientTime,
            DebugPingPayload::new
    );

    @Override
    public CorePayloadType<DebugPingPayload> type() {
        return TYPE;
    }
}
