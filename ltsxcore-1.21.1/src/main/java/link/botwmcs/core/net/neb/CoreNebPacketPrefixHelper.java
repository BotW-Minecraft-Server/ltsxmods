package link.botwmcs.core.net.neb;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Compact header helper for Core NEB frames.
 */
public final class CoreNebPacketPrefixHelper {
    private static final ThreadLocal<CoreNebPacketPrefixHelper> INSTANCES = ThreadLocal.withInitial(CoreNebPacketPrefixHelper::new);

    private int prefix = 0;
    private ResourceLocation type = null;

    private CoreNebPacketPrefixHelper() {
    }

    public static CoreNebPacketPrefixHelper get() {
        CoreNebPacketPrefixHelper helper = INSTANCES.get();
        helper.prefix = 0;
        helper.type = null;
        return helper;
    }

    public CoreNebPacketPrefixHelper index(ResourceLocation type) {
        int index = CoreNebNamespaceIndexManager.getNebIndex(type);
        this.type = type;
        this.prefix |= index;
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

    public static ResourceLocation getType(FriendlyByteBuf buf) {
        int fixed = buf.readUnsignedByte() & 0xFF;
        if (fixed >>> 7 == 0) {
            return buf.readResourceLocation();
        }
        if (fixed >>> 6 == 0) {
            return CoreNebNamespaceIndexManager.getIdentifier(buf.readUnsignedMedium(), false);
        }
        return CoreNebNamespaceIndexManager.getIdentifier(buf.readUnsignedShort(), true);
    }
}

