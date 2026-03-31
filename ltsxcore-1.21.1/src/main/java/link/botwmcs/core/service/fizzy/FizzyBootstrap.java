package link.botwmcs.core.service.fizzy;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.api.fizzier.overlay.IFizzyOverlayService;
import link.botwmcs.core.service.fizzy.overlay.FizzyOverlayService;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.core.util.CoreKeys;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public final class FizzyBootstrap {
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    private FizzyBootstrap() {
    }

    public static void bootstrap(Logger logger, IEventBus modBus) {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }

        CoreServices.registerIfAbsent(IFizzyOverlayService.class, new FizzyOverlayService());

        if (FMLEnvironment.dist.isClient()) {
            invokeClient(logger, "bootstrap", new Class<?>[]{Logger.class, IEventBus.class}, logger, modBus);
        }
    }

    public static void applyContributors(Logger logger) {
        if (FMLEnvironment.dist.isClient()) {
            invokeClient(logger, "applyContributors", new Class<?>[]{Logger.class}, logger);
        }
    }

    private static void invokeClient(Logger logger, String method, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> clazz = Class.forName("link.botwmcs.core.service.fizzy.client.FizzyClientBootstrap");
            clazz.getMethod(method, parameterTypes).invoke(null, args);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error("{}Failed to invoke Fizzy client bootstrap method '{}'.", CoreKeys.LOG_PREFIX, method, e);
        }
    }
}
