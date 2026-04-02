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
                PatternLayout.newBuilder().withPattern(logPattern).build(),
                false,
                new Property[0]
        );
        this.lineReader = lineReader;
    }

    @Override
    public void append(LogEvent event) {
        if (this.lineReader.isReading()) {
            this.lineReader.callWidget(LineReader.CLEAR);
        }

        this.lineReader.getTerminal().writer().print(this.getLayout().toSerializable(event).toString());

        if (this.lineReader.isReading()) {
            this.lineReader.callWidget(LineReader.REDRAW_LINE);
            this.lineReader.callWidget(LineReader.REDISPLAY);
        }
        this.lineReader.getTerminal().writer().flush();
    }
}
