package link.botwmcs.core.service.tty.console;

import java.util.Locale;

public enum TtyStyleColor {
    BLACK(0),
    RED(1),
    GREEN(2),
    YELLOW(3),
    BLUE(4),
    MAGENTA(5),
    CYAN(6),
    WHITE(7);

    private final int index;

    TtyStyleColor(int index) {
        this.index = index;
    }

    public int index() {
        return this.index;
    }

    public static TtyStyleColor byName(String value) {
        try {
            return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
