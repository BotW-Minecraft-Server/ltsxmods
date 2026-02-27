package link.botwmcs.core.service;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import link.botwmcs.core.util.CoreKeys;
import org.slf4j.Logger;

/**
 * Core service registry.
 * <p>
 * Thread/performance note: concurrent maps provide O(1) lookup on the hot path.
 */
public final class CoreServices {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentMap<Class<?>, Object> SERVICES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, CopyOnWriteArrayList<Object>> MULTI_SERVICES = new ConcurrentHashMap<>();

    private CoreServices() {
    }

    /**
     * Bootstrap hook for default service registrations.
     */
    public static void bootstrap() {
        LOGGER.info("{}CoreServices initialized.", CoreKeys.LOG_PREFIX);
    }

    public static <T> void register(Class<T> serviceType, T implementation) {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(implementation, "implementation");
        final Object previous = SERVICES.put(serviceType, implementation);
        if (previous != null && previous != implementation) {
            LOGGER.warn("{}Service '{}' replaced previous implementation '{}'.",
                    CoreKeys.LOG_PREFIX,
                    serviceType.getName(),
                    previous.getClass().getName());
        }
    }

    public static <T> T registerIfAbsent(Class<T> serviceType, T implementation) {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(implementation, "implementation");
        final Object existing = SERVICES.putIfAbsent(serviceType, implementation);
        return existing == null ? implementation : serviceType.cast(existing);
    }

    public static <T> T get(Class<T> serviceType) {
        return getOptional(serviceType).orElseThrow(() ->
                new IllegalStateException("Service not registered: " + serviceType.getName()));
    }

    public static <T> Optional<T> getOptional(Class<T> serviceType) {
        Objects.requireNonNull(serviceType, "serviceType");
        final Object service = SERVICES.get(serviceType);
        return service == null ? Optional.empty() : Optional.of(serviceType.cast(service));
    }

    /**
     * Register multi-implementation services (plugin list style).
     * TODO: add priority/unregister/hot-reload policy.
     */
    public static <T> List<T> registerMulti(Class<T> serviceType, T implementation) {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(implementation, "implementation");
        MULTI_SERVICES.computeIfAbsent(serviceType, ignored -> new CopyOnWriteArrayList<>()).add(implementation);
        return getMulti(serviceType);
    }

    public static <T> List<T> getMulti(Class<T> serviceType) {
        Objects.requireNonNull(serviceType, "serviceType");
        final CopyOnWriteArrayList<Object> services = MULTI_SERVICES.get(serviceType);
        if (services == null) {
            return List.of();
        }
        return services.stream().map(serviceType::cast).toList();
    }
}
