package link.botwmcs.core.service.tty.console;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jline.reader.LineReader;

final class ConsoleAppender extends AbstractAppender {
    static final String NAME = "LtsxCoreTtyConsole";

    private final LineReader lineReader;

    ConsoleAppender(LineReader lineReader, String logPattern) {
        super(
                NAME,
                null,
                PatternLayout.newBuilder()
                        .withPattern(logPattern)
                        .withDisableAnsi(false)
                        .withNoConsoleNoAnsi(false)
                        .build(),
                false,
                new Property[0]
        );
        this.lineReader = lineReader;
    }

    @Override
    public void append(LogEvent event) {
        final String message = this.getLayout().toSerializable(event).toString();
        if (this.lineReader.isReading()) {
            this.lineReader.printAbove(message);
        } else {
            this.lineReader.getTerminal().writer().print(message);
            this.lineReader.getTerminal().writer().flush();
        }
    }
}
