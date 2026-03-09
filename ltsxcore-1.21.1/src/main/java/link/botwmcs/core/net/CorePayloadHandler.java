package link.botwmcs.core.net;

@FunctionalInterface
public interface CorePayloadHandler<T extends CorePacketPayload> {
    void handle(T payload, CorePayloadContext context);
}

