package link.botwmcs.ltsxlogica.mastery.network;

import link.botwmcs.core.net.CorePacketPayload;
import link.botwmcs.core.net.CorePayloadType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record MasteryActionPayload(String action, ResourceLocation mastery, int slot, int inventorySlot,
                                   long stateRevision, long definitionRevision, long requestId) implements CorePacketPayload {
    public static final CorePayloadType<MasteryActionPayload> TYPE = new CorePayloadType<>(ResourceLocation.parse("ltsxlogica:mastery_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MasteryActionPayload> STREAM_CODEC = new StreamCodec<>() {
        public MasteryActionPayload decode(RegistryFriendlyByteBuf buf) {
            return new MasteryActionPayload(buf.readUtf(16), buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readLong());
        }
        public void encode(RegistryFriendlyByteBuf buf, MasteryActionPayload value) {
            buf.writeUtf(value.action(), 16); buf.writeResourceLocation(value.mastery()); buf.writeVarInt(value.slot());
            buf.writeVarInt(value.inventorySlot()); buf.writeLong(value.stateRevision()); buf.writeLong(value.definitionRevision()); buf.writeLong(value.requestId());
        }
    };
    public CorePayloadType<MasteryActionPayload> type() { return TYPE; }
}
