package link.botwmcs.ltsxlogica.heat.network;

import link.botwmcs.core.net.CorePacketPayload;
import link.botwmcs.core.net.CorePayloadType;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound payload: current effective entity temperature (fixed-point, scale=16).
 */
public record HeatEntityTemperaturePayload(int tempFixed) implements CorePacketPayload {
    public static final CorePayloadType<HeatEntityTemperaturePayload> TYPE = new CorePayloadType<>(
            ResourceLocation.fromNamespaceAndPath(LTSXLogicA.MODID, "heat_entity_temperature")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, HeatEntityTemperaturePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            HeatEntityTemperaturePayload::tempFixed,
            HeatEntityTemperaturePayload::new
    );

    @Override
    public CorePayloadType<HeatEntityTemperaturePayload> type() {
        return TYPE;
    }
}
