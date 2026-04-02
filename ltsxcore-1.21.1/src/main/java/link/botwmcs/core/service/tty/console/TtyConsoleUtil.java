package link.botwmcs.core.service.tty.console;

import com.mojang.brigadier.StringReader;

public final class TtyConsoleUtil {
    private TtyConsoleUtil() {
    }

    public static StringReader prepareStringReader(String buffer) {
        final StringReader stringReader = new StringReader(buffer);
        if (stringReader.canRead() && stringReader.peek() == '/') {
            stringReader.skip();
        }
        return stringReader;
    }
}
