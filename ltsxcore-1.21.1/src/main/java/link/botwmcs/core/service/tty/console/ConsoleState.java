package link.botwmcs.core.service.tty.console;

import org.jline.reader.LineReader;

public record ConsoleState(
        LineReader lineReader,
        DelegatingCompleter completer,
        DelegatingHighlighter highlighter,
        DelegatingParser parser
) {
}
