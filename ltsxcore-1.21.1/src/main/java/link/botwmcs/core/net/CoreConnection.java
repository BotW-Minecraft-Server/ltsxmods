package link.botwmcs.core.net;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Core-owned connection abstraction.
 */
public interface CoreConnection {
    PacketFlow flow();

    void send(CorePacketPayload payload);

    void sendVanilla(CustomPacketPayload payload);
}

