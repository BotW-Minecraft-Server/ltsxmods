package link.botwmcs.core.service.tty;

import java.util.List;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.service.tty.console.ConsoleSetup;
import link.botwmcs.core.service.tty.console.ConsoleState;
import link.botwmcs.core.service.tty.console.ConsoleThread;
import link.botwmcs.core.service.tty.console.MinecraftCommandCompleter;
import link.botwmcs.core.service.tty.console.MinecraftCommandHighlighter;
import link.botwmcs.core.service.tty.console.MinecraftConsoleParser;
import link.botwmcs.core.service.tty.console.TtyStyleColor;
import link.botwmcs.core.util.CoreKeys;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;

public final class TtyService {
    private final Logger logger;
    private final Object lifecycleLock = new Object();

    private volatile ConsoleState consoleState;
    private volatile ConsoleThread consoleThread;
    private volatile boolean vanillaConsoleThreadIntercepted;

    public TtyService(Logger logger) {
        this.logger = logger;
    }

    public void start(DedicatedServer server) {
        if (!CoreConfig.ttyEnabled()) {
            return;
        }

        synchronized (this.lifecycleLock) {
            final ConsoleState state = this.ensureConsoleState();
            state.completer().delegateTo(new MinecraftCommandCompleter(server));
            state.highlighter().delegateTo(new MinecraftCommandHighlighter(server, this.resolveHighlightColors()));
            state.parser().delegateTo(new MinecraftConsoleParser(server));

            if (this.consoleThread != null && this.consoleThread.isAlive()) {
                return;
            }

            final ConsoleThread thread = new ConsoleThread(server, state.lineReader());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(this.logger));
            thread.start();

            this.consoleThread = thread;
            this.logger.info("{}TTY console thread started.", CoreKeys.LOG_PREFIX);
        }
    }

    public void stop() {
        synchronized (this.lifecycleLock) {
            if (this.consoleThread != null) {
                this.consoleThread.interrupt();
            }
            this.consoleThread = null;
        }
    }

    public void onVanillaConsoleThreadIntercepted() {
        synchronized (this.lifecycleLock) {
            if (this.vanillaConsoleThreadIntercepted) {
                return;
            }
            this.vanillaConsoleThreadIntercepted = true;
        }
        this.logger.info("{}Shutting down vanilla console thread...", CoreKeys.LOG_PREFIX);
    }

    public void logPlayerCommand(String playerName, String command) {
        if (!CoreConfig.ttyEnabled() || !CoreConfig.ttyLogPlayerCommands()) {
            return;
        }
        this.logger.info("{}{} issued server command: /{}", CoreKeys.LOG_PREFIX, playerName, command);
    }

    public Status status() {
        final ConsoleThread thread = this.consoleThread;
        final ConsoleState state = this.consoleState;
        return new Status(
                true,
                CoreConfig.ttyEnabled(),
                state != null,
                thread != null && thread.isAlive(),
                CoreConfig.ttyLogPlayerCommands(),
                ConsoleSetup.historyFile().toAbsolutePath().normalize().toString()
        );
    }

    private ConsoleState ensureConsoleState() {
        ConsoleState state = this.consoleState;
        if (state != null) {
            return state;
        }

        synchronized (this.lifecycleLock) {
            if (this.consoleState == null) {
                this.consoleState = ConsoleSetup.init(CoreConfig.ttyLogPattern());
            }
            return this.consoleState;
        }
    }

    private TtyStyleColor[] resolveHighlightColors() {
        final List<String> configuredColors = CoreConfig.ttyHighlightColors();
        return configuredColors.stream()
                .map(TtyStyleColor::byName)
                .filter(color -> color != null)
                .toArray(TtyStyleColor[]::new);
    }

    public record Status(
            boolean supported,
            boolean enabled,
            boolean installed,
            boolean running,
            boolean logPlayerCommands,
            String historyFile
    ) {
        public static Status unsupported() {
            return new Status(
                    false,
                    false,
                    false,
                    false,
                    false,
                    ConsoleSetup.historyFile().toAbsolutePath().normalize().toString()
            );
        }
    }
}
