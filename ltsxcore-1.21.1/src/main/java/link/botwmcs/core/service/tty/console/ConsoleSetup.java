package link.botwmcs.core.service.tty.console;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

public final class ConsoleSetup {
    private static final Path HISTORY_FILE = Paths.get(".console_history");

    private ConsoleSetup() {
    }

    public static ConsoleState init(String logPattern) {
        final DelegatingCompleter delegatingCompleter = new DelegatingCompleter();
        final DelegatingHighlighter delegatingHighlighter = new DelegatingHighlighter();
        final DelegatingParser delegatingParser = new DelegatingParser();
        final LineReader lineReader = buildLineReader(delegatingCompleter, delegatingHighlighter, delegatingParser);

        final ConsoleAppender consoleAppender = new ConsoleAppender(lineReader, logPattern);
        consoleAppender.start();

        final Logger logger = (Logger) LogManager.getRootLogger();
        final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final LoggerConfig loggerConfig = loggerContext.getConfiguration().getLoggerConfig(logger.getName());

        loggerConfig.removeAppender(ConsoleAppender.NAME);
        loggerConfig.removeAppender("SysOut");
        loggerConfig.addAppender(consoleAppender, loggerConfig.getLevel(), null);
        loggerContext.updateLoggers();

        return new ConsoleState(lineReader, delegatingCompleter, delegatingHighlighter, delegatingParser);
    }

    public static Path historyFile() {
        return HISTORY_FILE;
    }

    private static LineReader buildLineReader(
            DelegatingCompleter completer,
            DelegatingHighlighter highlighter,
            DelegatingParser parser
    ) {
        System.setProperty("org.jline.reader.support.parsedline", "true");

        return LineReaderBuilder.builder()
                .appName("Dedicated Server")
                .variable(LineReader.HISTORY_FILE, HISTORY_FILE)
                .completer(completer)
                .highlighter(highlighter)
                .parser(parser)
                .completionMatcher(new MinecraftCompletionMatcher())
                .option(LineReader.Option.INSERT_TAB, false)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .option(LineReader.Option.COMPLETE_IN_WORD, true)
                .build();
    }
}
