package link.botwmcs.ltsxlogica.mastery.network;

import link.botwmcs.core.net.CorePacketPayload;
import link.botwmcs.core.net.CorePayloadType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Bounded chunks keep individual core frames small even with many registered masteries. */
public record MasterySyncPayload(long transferId, int index, int count, boolean open, String json) implements CorePacketPayload {
    public static final int CHUNK_CHARS = 8192;
    public static final int MAX_CHUNKS = 128;
    public static final CorePayloadType<MasterySyncPayload> TYPE = new CorePayloadType<>(ResourceLocation.parse("ltsxlogica:mastery_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MasterySyncPayload> STREAM_CODEC = new StreamCodec<>() {
        public MasterySyncPayload decode(RegistryFriendlyByteBuf buf) { return new MasterySyncPayload(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(CHUNK_CHARS)); }
        public void encode(RegistryFriendlyByteBuf buf, MasterySyncPayload value) {
            buf.writeLong(value.transferId()); buf.writeVarInt(value.index()); buf.writeVarInt(value.count()); buf.writeBoolean(value.open()); buf.writeUtf(value.json(), CHUNK_CHARS);
        }
    };
    public CorePayloadType<MasterySyncPayload> type() { return TYPE; }
    /** Preserve surrogate pairs so UTF-8 encoding of separate chunks cannot damage names. */
    public static java.util.List<String> split(String json) {
        var chunks = new java.util.ArrayList<String>();
        for (int from = 0; from < json.length();) {
            int to = Math.min(json.length(), from + CHUNK_CHARS);
            if (to < json.length() && Character.isHighSurrogate(json.charAt(to - 1))) to--;
            chunks.add(json.substring(from, to));
            if (chunks.size() > MAX_CHUNKS) throw new IllegalStateException("Mastery snapshot exceeds sync limit");
            from = to;
        }
        return chunks.isEmpty() ? java.util.List.of("") : java.util.List.copyOf(chunks);
    }
}
