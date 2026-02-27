package link.botwmcs.core.config;

import java.util.Objects;
import link.botwmcs.core.util.CoreKeys;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Central config definition and registration for ltsxcore.
 */
public final class CoreConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG = BUILDER
            .comment("Enable core debug logs.")
            .define("enableDebug", false);

    public static final ModConfigSpec.BooleanValue MODULE_LOGGING = BUILDER
            .comment("Enable module lifecycle logging.")
            .define("moduleLogging", true);

    public static final ModConfigSpec.IntValue SCHEDULER_BUDGET_MICROS = BUILDER
            .comment("Per-tick task scheduler budget in microseconds.")
            .defineInRange("schedulerBudgetMicros", 1_000, 0, 200_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CoreConfig() {
    }

    /**
     * Registers the COMMON config spec.
     */
    public static void init(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus");
        final ModContainer activeContainer = ModLoadingContext.get().getActiveContainer();
        if (activeContainer == null) {
            throw new IllegalStateException(CoreKeys.LOG_PREFIX + "No active ModContainer while registering config.");
        }
        activeContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
    }

    public static boolean enableDebug() {
        return ENABLE_DEBUG.get();
    }

    public static boolean moduleLogging() {
        return MODULE_LOGGING.get();
    }

    public static int schedulerBudgetMicros() {
        return SCHEDULER_BUDGET_MICROS.get();
    }
}
