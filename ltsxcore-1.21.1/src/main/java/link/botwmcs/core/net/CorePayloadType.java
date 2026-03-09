package link.botwmcs.core.net;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Typed id for CoreNetworking payloads.
 */
public record CorePayloadType<T extends CorePacketPayload>(ResourceLocation id) {
    public CorePayloadType {
        Objects.requireNonNull(id, "id");
    }
}

