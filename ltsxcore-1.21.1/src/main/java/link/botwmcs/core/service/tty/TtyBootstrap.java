package link.botwmcs.core.service.tty;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.core.util.CoreKeys;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public final class TtyBootstrap {
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    private TtyBootstrap() {
    }

    public static void bootstrap(Logger logger, IEventBus neoForgeBus) {
        if (FMLEnvironment.dist.isClient()) {
            return;
        }
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }

        CoreServices.registerIfAbsent(TtyService.class, new TtyService(logger));
        TtyLifecycle.init(neoForgeBus);
        logger.info("{}TTY service registered.", CoreKeys.LOG_PREFIX);
    }

    public static Optional<TtyService> serviceOrNull() {
        return CoreServices.getOptional(TtyService.class);
    }

    public static boolean shouldReplaceVanillaConsole() {
        return !FMLEnvironment.dist.isClient() && CoreConfig.ttyEnabled();
    }

    public static void onVanillaConsoleThreadIntercepted() {
        serviceOrNull().ifPresent(TtyService::onVanillaConsoleThreadIntercepted);
    }

    public static void logPlayerCommand(String playerName, String command) {
        serviceOrNull().ifPresent(service -> service.logPlayerCommand(playerName, command));
    }
}
