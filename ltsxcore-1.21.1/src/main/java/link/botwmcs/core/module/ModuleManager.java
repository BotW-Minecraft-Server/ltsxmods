package link.botwmcs.core.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import link.botwmcs.core.api.module.CoreModuleContext;
import link.botwmcs.core.api.module.ICoreModule;
import link.botwmcs.core.util.CoreKeys;

/**
 * Module discovery and lifecycle manager.
 * <p>
 * Thread/performance note: discovery runs once during bootstrap; runtime path is read-only queries.
 */
public final class ModuleManager {
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    private static final CopyOnWriteArrayList<ICoreModule> LOADED_MODULES = new CopyOnWriteArrayList<>();

    private ModuleManager() {
    }

    /**
     * Discover modules using ServiceLoader and invoke them in loadOrder order.
     */
    public static void discoverAndLoad(CoreModuleContext context) {
        if (!LOADED.compareAndSet(false, true)) {
            context.logger().debug("{}ModuleManager already initialized.", CoreKeys.LOG_PREFIX);
            return;
        }

        final List<ICoreModule> discovered = new ArrayList<>();
        final ServiceLoader<ICoreModule> loader =
                ServiceLoader.load(ICoreModule.class, Thread.currentThread().getContextClassLoader());

        try {
            for (ICoreModule module : loader) {
                discovered.add(module);
            }
        } catch (ServiceConfigurationError error) {
            context.logger().error("{}ServiceLoader failed while scanning modules.", CoreKeys.LOG_PREFIX, error);
        }

        discovered.sort(Comparator
                .comparingInt(ICoreModule::loadOrder)
                .thenComparing(ICoreModule::moduleId));

        for (ICoreModule module : discovered) {
            try {
                module.onRegister(context);
                LOADED_MODULES.add(module);
                context.logger().info("{}Loaded module '{}' (order={}).",
                        CoreKeys.LOG_PREFIX,
                        module.moduleId(),
                        module.loadOrder());
            } catch (Throwable throwable) {
                context.logger().error("{}Failed to register module '{}'.",
                        CoreKeys.LOG_PREFIX,
                        module.moduleId(),
                        throwable);
            }
        }

        final String loadedList = LOADED_MODULES.stream()
                .map(module -> module.moduleId() + "@" + module.loadOrder())
                .collect(Collectors.joining(", "));
        context.logger().info("{}Module loading finished. count={}, modules=[{}]",
                CoreKeys.LOG_PREFIX,
                LOADED_MODULES.size(),
                loadedList);
    }

    /**
     * Returns an immutable snapshot of loaded modules.
     */
    public static List<ICoreModule> getLoadedModules() {
        return List.copyOf(LOADED_MODULES);
    }
}
