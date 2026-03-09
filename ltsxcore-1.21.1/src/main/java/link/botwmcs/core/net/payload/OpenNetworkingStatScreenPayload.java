package link.botwmcs.core.net.payload;

import link.botwmcs.core.net.CorePacketPayload;
import link.botwmcs.core.net.CorePayloadType;
import link.botwmcs.core.util.CoreIds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Clientbound command payload that opens the core networking stat GUI.
 */
public record OpenNetworkingStatScreenPayload(boolean open) implements CorePacketPayload {
    public static final CorePayloadType<OpenNetworkingStatScreenPayload> TYPE = new CorePayloadType<>(CoreIds.id("open_networking_stat_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNetworkingStatScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            OpenNetworkingStatScreenPayload::open,
            OpenNetworkingStatScreenPayload::new
    );

    public static OpenNetworkingStatScreenPayload openScreen() {
        return new OpenNetworkingStatScreenPayload(true);
    }

    @Override
    public CorePayloadType<OpenNetworkingStatScreenPayload> type() {
        return TYPE;
    }
}
