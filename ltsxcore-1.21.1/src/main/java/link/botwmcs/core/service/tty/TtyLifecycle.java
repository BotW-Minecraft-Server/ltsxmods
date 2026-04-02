package link.botwmcs.core.service.tty;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.dedicated.DedicatedServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class TtyLifecycle {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private TtyLifecycle() {
    }

    public static void init(IEventBus neoForgeBus) {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        neoForgeBus.addListener(TtyLifecycle::onServerStarting);
        neoForgeBus.addListener(TtyLifecycle::onServerStopping);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        if (!(event.getServer() instanceof DedicatedServer dedicatedServer)) {
            return;
        }
        TtyBootstrap.serviceOrNull().ifPresent(service -> service.start(dedicatedServer));
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        TtyBootstrap.serviceOrNull().ifPresent(TtyService::stop);
    }
}
