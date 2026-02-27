package link.botwmcs.core.net.payload;

import link.botwmcs.core.util.CoreIds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Example payload used to verify core networking and server-side handling flow.
 */
public record DebugPingPayload(long clientTime) implements CustomPacketPayload {
    public static final Type<DebugPingPayload> TYPE = new Type<>(CoreIds.id("debug_ping"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugPingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            DebugPingPayload::clientTime,
            DebugPingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
