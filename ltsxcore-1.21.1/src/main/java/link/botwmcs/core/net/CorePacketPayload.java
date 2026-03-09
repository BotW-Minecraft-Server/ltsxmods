package link.botwmcs.core.net;

/**
 * Core-owned payload abstraction.
 * <p>
 * Payloads using this interface are transported on the CoreNetworking bus and can be optimized by NEB internals
 * without affecting vanilla/global networking behavior.
 */
public interface CorePacketPayload {
    CorePayloadType<? extends CorePacketPayload> type();
}

