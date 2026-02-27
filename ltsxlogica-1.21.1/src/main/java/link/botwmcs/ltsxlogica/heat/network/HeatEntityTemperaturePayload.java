package link.botwmcs.ltsxlogica.heat.network;

import link.botwmcs.ltsxlogica.LTSXLogicA;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound payload: current effective entity temperature (fixed-point, scale=16).
 */
public record HeatEntityTemperaturePayload(int tempFixed) implements CustomPacketPayload {
    public static final Type<HeatEntityTemperaturePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LTSXLogicA.MODID, "heat_entity_temperature")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, HeatEntityTemperaturePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            HeatEntityTemperaturePayload::tempFixed,
            HeatEntityTemperaturePayload::new
    );

    @Override
    public Type<HeatEntityTemperaturePayload> type() {
        return TYPE;
    }
}

