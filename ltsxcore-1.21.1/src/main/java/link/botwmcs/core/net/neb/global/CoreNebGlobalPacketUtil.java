package link.botwmcs.core.net.neb.global;

import link.botwmcs.core.net.payload.CoreNebBatchPayload;
import link.botwmcs.core.net.payload.CoreNebDirectPayload;
import link.botwmcs.core.net.payload.CoreNebGlobalBatchPayload;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet helpers for global NEB mixin mode.
 */
public final class CoreNebGlobalPacketUtil {
    private CoreNebGlobalPacketUtil() {
    }

    public static ResourceLocation getTrueType(Packet<?> packet) {
        if (packet instanceof ServerboundCustomPayloadPacket(CustomPacketPayload payload)) {
            return payload.type().id();
        }
        if (packet instanceof ClientboundCustomPayloadPacket(CustomPacketPayload payload)) {
            return payload.type().id();
        }
        return packet.type().id();
    }

    public static Object getTruePacket(Packet<?> packet) {
        if (packet instanceof ServerboundCustomPayloadPacket(CustomPacketPayload payload)) {
            return payload;
        }
        if (packet instanceof ClientboundCustomPayloadPacket(CustomPacketPayload payload)) {
            return payload;
        }
        return packet;
    }

    public static boolean isNebTransportPacket(Object truePacket, ResourceLocation trueType) {
        if (truePacket instanceof CoreNebGlobalBatchPayload) {
            return true;
        }
        if (trueType == null) {
            return false;
        }
        return trueType.equals(CoreNebGlobalBatchPayload.TYPE.id())
                || trueType.equals(CoreNebBatchPayload.TYPE.id())
                || trueType.equals(CoreNebDirectPayload.TYPE.id());
    }
}
