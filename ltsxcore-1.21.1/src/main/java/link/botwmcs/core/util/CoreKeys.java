package link.botwmcs.core.util;

/**
 * Shared key/prefix constants to avoid scattered hard-coded strings.
 */
public final class CoreKeys {
    public static final String LOG_PREFIX = "[ltsxcore] ";
    public static final String TRANSLATION_PREFIX = CoreIds.MOD_ID + ".";
    public static final String COMMAND_TRANSLATION_PREFIX = TRANSLATION_PREFIX + "command.";
    public static final String PLAYER_PERSISTENT_ROOT = CoreIds.MOD_ID;

    private CoreKeys() {
    }
}
