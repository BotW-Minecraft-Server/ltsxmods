package link.botwmcs.core.net.payload;

import link.botwmcs.core.util.CoreIds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Core bus transport payload for bypass path (compatibility blacklist).
 */
public record CoreNebDirectPayload(ResourceLocation coreType, byte[] data) implements CustomPacketPayload {
    public static final Type<CoreNebDirectPayload> TYPE = new Type<>(CoreIds.id("core_neb_direct"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreNebDirectPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            CoreNebDirectPayload::coreType,
            ByteBufCodecs.BYTE_ARRAY,
            CoreNebDirectPayload::data,
            CoreNebDirectPayload::new
    );

    @Override
    public Type<CoreNebDirectPayload> type() {
        return TYPE;
    }
}

