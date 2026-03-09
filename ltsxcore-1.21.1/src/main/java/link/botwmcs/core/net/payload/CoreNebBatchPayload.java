package link.botwmcs.core.net.payload;

import link.botwmcs.core.util.CoreIds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Core bus transport payload that carries NEB-optimized batched frames.
 */
public record CoreNebBatchPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<CoreNebBatchPayload> TYPE = new Type<>(CoreIds.id("core_neb_batch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreNebBatchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY,
            CoreNebBatchPayload::data,
            CoreNebBatchPayload::new
    );

    @Override
    public Type<CoreNebBatchPayload> type() {
        return TYPE;
    }
}

