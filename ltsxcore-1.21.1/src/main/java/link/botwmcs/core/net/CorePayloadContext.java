package link.botwmcs.core.net;

import java.util.concurrent.CompletableFuture;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Core networking handler context.
 */
public final class CorePayloadContext {
    private final IPayloadContext delegate;

    public CorePayloadContext(IPayloadContext delegate) {
        this.delegate = delegate;
    }

    public IPayloadContext delegate() {
        return delegate;
    }

    public Player player() {
        return delegate.player();
    }

    public PacketFlow flow() {
        return delegate.flow();
    }

    public CompletableFuture<Void> enqueueWork(Runnable task) {
        return delegate.enqueueWork(task);
    }
}

