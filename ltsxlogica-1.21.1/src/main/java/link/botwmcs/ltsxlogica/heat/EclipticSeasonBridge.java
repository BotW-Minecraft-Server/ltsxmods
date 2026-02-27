package link.botwmcs.ltsxlogica.heat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import link.botwmcs.ltsxlogica.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Runtime bridge for optional Ecliptic Seasons integration.
 *
 * Uses reflection so this mod does not require Ecliptic as a hard compile-time dependency.
 */
final class EclipticSeasonBridge {
    private static final String MOD_ID = "eclipticseasons";
    private static final String API_CLASS = "com.teamtea.eclipticseasons.api.EclipticSeasonsApi";
    private static final int VALID_TERM_COUNT = 24;

    private static final Map<ResourceKey<Level>, CachedOffset> OFFSET_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean reflectionAttempted = false;
    private static volatile boolean reflectionReady = false;

    private static Method apiGetInstance;
    private static Method apiGetSolarTerm;
    private static Method termGetTemperatureChange;
    private static Class<?> termClassForTemperatureChange;

    private EclipticSeasonBridge() {
    }

    static int resolveSeasonalOffsetFixed(Level level) {
        if (!Config.isHeatEclipticAmbientEnabled()) {
            return 0;
        }
        if (!ModList.get().isLoaded(MOD_ID)) {
            return 0;
        }

        long tick = level.getGameTime();
        ResourceKey<Level> dimension = level.dimension();
        CachedOffset cached = OFFSET_CACHE.get(dimension);
        if (cached != null && cached.tick == tick) {
            return cached.offsetFixed;
        }

        int computed = computeOffsetFixed(level);
        OFFSET_CACHE.put(dimension, new CachedOffset(tick, computed));
        return computed;
    }

    private static int computeOffsetFixed(Level level) {
        Object solarTerm = resolveCurrentSolarTerm(level);
        if (solarTerm == null) {
            return 0;
        }

        int termIndex = resolveTermIndex(solarTerm);
        if (termIndex < 0 || termIndex >= VALID_TERM_COUNT) {
            return 0;
        }

        double termDelta = Config.getHeatEclipticTermBaseUnit(termIndex);
        if (Config.isHeatEclipticPreferApiTermDelta()) {
            Double apiDelta = resolveTermDeltaFromApi(solarTerm);
            if (apiDelta != null && Double.isFinite(apiDelta)) {
                termDelta = apiDelta;
            }
        }

        double deltaCelsius = termDelta * Config.getHeatEclipticTermUnitToCelsius()
                + Config.getHeatEclipticGlobalOffsetCelsius();
        return Math.round((float) (deltaCelsius * HeatManager.FIXED_SCALE));
    }

    private static Object resolveCurrentSolarTerm(Level level) {
        if (!ensureReflectionReady()) {
            return null;
        }
        try {
            Object api = apiGetInstance.invoke(null);
            if (api == null) {
                return null;
            }
            return apiGetSolarTerm.invoke(api, level);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static int resolveTermIndex(Object solarTerm) {
        if (solarTerm instanceof Enum<?> enumTerm) {
            return enumTerm.ordinal();
        }
        try {
            Method ordinal = solarTerm.getClass().getMethod("ordinal");
            Object value = ordinal.invoke(solarTerm);
            return value instanceof Number n ? n.intValue() : -1;
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static Double resolveTermDeltaFromApi(Object solarTerm) {
        try {
            if (termGetTemperatureChange == null || termClassForTemperatureChange != solarTerm.getClass()) {
                termGetTemperatureChange = solarTerm.getClass().getMethod("getTemperatureChange");
                termClassForTemperatureChange = solarTerm.getClass();
            }
            Object value = termGetTemperatureChange.invoke(solarTerm);
            if (value instanceof Number n) {
                return n.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static boolean ensureReflectionReady() {
        if (reflectionAttempted) {
            return reflectionReady;
        }
        synchronized (EclipticSeasonBridge.class) {
            if (reflectionAttempted) {
                return reflectionReady;
            }

            reflectionAttempted = true;
            try {
                Class<?> apiClass = Class.forName(API_CLASS);
                apiGetInstance = apiClass.getMethod("getInstance");
                apiGetSolarTerm = apiClass.getMethod("getSolarTerm", Level.class);
                reflectionReady = true;
            } catch (ReflectiveOperationException ignored) {
                reflectionReady = false;
            }
            return reflectionReady;
        }
    }

    private record CachedOffset(long tick, int offsetFixed) {
    }
}
