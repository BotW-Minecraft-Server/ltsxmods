package link.botwmcs.core.net.neb.global;

import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Compact payload type header helper for global CustomPacketPayload codec mixin.
 */
public final class CoreNebGlobalPacketPrefixHelper {
    private static final ThreadLocal<CoreNebGlobalPacketPrefixHelper> INSTANCES =
            ThreadLocal.withInitial(CoreNebGlobalPacketPrefixHelper::new);

    private int prefix = 0;
    private ResourceLocation type = null;

    private CoreNebGlobalPacketPrefixHelper() {
    }

    public static CoreNebGlobalPacketPrefixHelper get() {
        CoreNebGlobalPacketPrefixHelper helper = INSTANCES.get();
        helper.prefix = 0;
        helper.type = null;
        return helper;
    }

    public CoreNebGlobalPacketPrefixHelper index(ResourceLocation type, Map<ResourceLocation, ?> payloadCodecMap) {
        this.type = type;
        this.prefix = CoreNebGlobalPrefixIndexCache.get(payloadCodecMap).getNebIndex(type);
        return this;
    }

    public void save(FriendlyByteBuf buf) {
        if (prefix >>> 31 == 0) {
            buf.writeByte(prefix >>> 24);
            buf.writeResourceLocation(type);
            return;
        }
        if ((prefix >>> 30 & 1) == 1) {
            buf.writeMedium(prefix >>> 8);
        } else {
            buf.writeInt(prefix);
        }
    }

    public static ResourceLocation getType(FriendlyByteBuf buf, Map<ResourceLocation, ?> payloadCodecMap) {
        int fixed = buf.readUnsignedByte() & 0xFF;
        CoreNebGlobalPrefixIndex index = CoreNebGlobalPrefixIndexCache.get(payloadCodecMap);
        if ((fixed & 0x80) == 0) {
            return buf.readResourceLocation();
        }
        if ((fixed & 0x40) == 0) {
            ResourceLocation type = index.getIdentifier(buf.readUnsignedMedium(), false);
            if (type == null) {
                throw new IllegalStateException("Invalid NEB medium packet type index.");
            }
            return type;
        }
        ResourceLocation type = index.getIdentifier(buf.readUnsignedShort(), true);
        if (type == null) {
            throw new IllegalStateException("Invalid NEB tight packet type index.");
        }
        return type;
    }
}
