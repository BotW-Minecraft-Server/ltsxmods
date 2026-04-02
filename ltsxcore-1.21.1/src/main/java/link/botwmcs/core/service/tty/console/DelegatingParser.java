package link.botwmcs.core.service.tty.console;

import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.reader.impl.DefaultParser;

public final class DelegatingParser implements Parser {
    private volatile Parser delegate = new DefaultParser();

    public void delegateTo(Parser parser) {
        this.delegate = parser;
    }

    @Override
    public ParsedLine parse(String line, int cursor, ParseContext context) throws SyntaxError {
        return this.delegate.parse(line, cursor, context);
    }
}
