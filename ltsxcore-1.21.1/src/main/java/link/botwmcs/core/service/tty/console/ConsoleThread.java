package link.botwmcs.core.service.tty.console;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

public final class ConsoleThread extends Thread {
    private static final String TERMINAL_PROMPT = "> ";
    private static final String STOP_COMMAND = "stop";

    private final DedicatedServer server;
    private final LineReader lineReader;

    public ConsoleThread(DedicatedServer server, LineReader lineReader) {
        super("LTSX TTY Console");
        this.server = server;
        this.lineReader = lineReader;
    }

    @Override
    public void run() {
        this.acceptInput();
    }

    private static boolean isRunning(MinecraftServer server) {
        return !server.isStopped() && server.isRunning();
    }

    private void acceptInput() {
        while (isRunning(this.server)) {
            try {
                final String input = this.lineReader.readLine(TERMINAL_PROMPT).trim();
                if (input.isEmpty()) {
                    continue;
                }
                this.server.handleConsoleInput(input, this.server.createCommandSourceStack());
                if (input.equals(STOP_COMMAND)) {
                    break;
                }
            } catch (EndOfFileException | UserInterruptException ex) {
                this.server.handleConsoleInput(STOP_COMMAND, this.server.createCommandSourceStack());
                break;
            }
        }
    }
}
