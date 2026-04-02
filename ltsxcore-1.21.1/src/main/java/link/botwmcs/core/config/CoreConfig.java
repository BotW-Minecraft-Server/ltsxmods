package link.botwmcs.core.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import link.botwmcs.core.service.tty.console.TtyStyleColor;
import link.botwmcs.core.util.CoreKeys;
import net.minecraft.resources.ResourceLocation;
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

    public static final ModConfigSpec.BooleanValue TTY_ENABLED = BUILDER
            .comment("Enable enhanced dedicated server TTY console.")
            .define("ttyEnabled", true);

    public static final ModConfigSpec.ConfigValue<String> TTY_LOG_PATTERN = BUILDER
            .comment("Log4j pattern used by the enhanced TTY console.")
            .define(
                    "ttyLogPattern",
                    "%highlight{[%d{HH:mm:ss} %level] [%t]: [%logger{1}]}{FATAL=red, ERROR=red, WARN=yellow, INFO=default, DEBUG=yellow, TRACE=blue} %msg%n"
            );

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TTY_HIGHLIGHT_COLORS = BUILDER
            .comment("TTY command argument highlight colors. Possible values: [BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE]")
            .defineListAllowEmpty(
                    List.of("ttyHighlightColors"),
                    List.of("CYAN", "YELLOW", "GREEN", "MAGENTA", "BLUE"),
                    CoreConfig::isTtyColorEntry
            );

    public static final ModConfigSpec.BooleanValue TTY_LOG_PLAYER_COMMANDS = BUILDER
            .comment("Whether to log commands executed by players to the enhanced TTY console.")
            .define("ttyLogPlayerCommands", true);

    public static final ModConfigSpec.IntValue SCHEDULER_BUDGET_MICROS = BUILDER
            .comment("Per-tick task scheduler budget in microseconds.")
            .defineInRange("schedulerBudgetMicros", 1_000, 0, 200_000);

    public static final ModConfigSpec.BooleanValue NEB_COMPATIBLE_MODE = BUILDER
            .comment("Enable NEB compatibility mode. When enabled, nebBlackList packets bypass NEB aggregation/prefix.")
            .define("nebCompatibleMode", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> NEB_BLACKLIST = BUILDER
            .comment("NEB compatibility blacklist. Packet ids in this list bypass NEB.")
            .defineListAllowEmpty(
                    List.of("nebBlackList"),
                    List.of(
                            "minecraft:command_suggestion",
                            "minecraft:command_suggestions",
                            "minecraft:commands",
                            "minecraft:chat_command",
                            "minecraft:chat_command_signed",
                            "minecraft:player_info_update",
                            "minecraft:player_info_remove",
                            "minecraft:register",
                            "minecraft:unregister",
                            "velocity:player_info",
                            "velocity:modern_forwarding"
                    ),
                    CoreConfig::isPacketTypeEntry
            );

    public static final ModConfigSpec.IntValue NEB_CONTEXT_LEVEL = BUILDER
            .comment("NEB zstd context window log2 size, range [21,25] => [2MB,32MB], default 23 (8MB).")
            .defineInRange("nebContextLevel", 23, 21, 25);

    public static final ModConfigSpec.IntValue NEB_FLUSH_PERIOD_MS = BUILDER
            .comment("NEB aggregation flush period in milliseconds.")
            .defineInRange("nebFlushPeriodMs", 20, 1, 200);

    public static final ModConfigSpec.BooleanValue NEB_DEBUG_LOG = BUILDER
            .comment("Enable NEB debug logs.")
            .define("nebDebugLog", false);

    public static final ModConfigSpec.BooleanValue NEB_GLOBAL_MIXIN_ENABLED = BUILDER
            .comment("Enable global NEB mixin mode. When enabled, Connection send path is intercepted globally in PLAY phase.")
            .define("nebGlobalMixinEnabled", false);

    public static final ModConfigSpec.BooleanValue NEB_GLOBAL_FULL_PACKET_STAT = BUILDER
            .comment("Enable full packet stat in global NEB mode. When enabled, PacketEncoder/PacketDecoder also record BYPASS packets.")
            .define("nebGlobalFullPacketStat", false);

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

    public static boolean ttyEnabled() {
        return TTY_ENABLED.get();
    }

    public static String ttyLogPattern() {
        return TTY_LOG_PATTERN.get();
    }

    public static List<String> ttyHighlightColors() {
        return List.copyOf(TTY_HIGHLIGHT_COLORS.get());
    }

    public static boolean ttyLogPlayerCommands() {
        return TTY_LOG_PLAYER_COMMANDS.get();
    }

    public static int schedulerBudgetMicros() {
        return SCHEDULER_BUDGET_MICROS.get();
    }

    public static boolean nebCompatibleMode() {
        return NEB_COMPATIBLE_MODE.get();
    }

    public static Set<String> nebBlackList() {
        return new LinkedHashSet<>(NEB_BLACKLIST.get());
    }

    public static int nebContextLevel() {
        return NEB_CONTEXT_LEVEL.get();
    }

    public static int nebFlushPeriodMs() {
        return NEB_FLUSH_PERIOD_MS.get();
    }

    public static boolean nebDebugLog() {
        return NEB_DEBUG_LOG.get();
    }

    public static boolean nebGlobalMixinEnabled() {
        return NEB_GLOBAL_MIXIN_ENABLED.get();
    }

    public static boolean nebGlobalFullPacketStat() {
        return NEB_GLOBAL_FULL_PACKET_STAT.get();
    }

    private static boolean isPacketTypeEntry(Object value) {
        return value instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    private static boolean isTtyColorEntry(Object value) {
        return value instanceof String s && TtyStyleColor.byName(s) != null;
    }
}
