package link.botwmcs.core.service.tty.console;

import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

public final class DelegatingCompleter implements Completer {
    private volatile Completer delegate;

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        final Completer current = this.delegate;
        if (current != null) {
            current.complete(reader, line, candidates);
        }
    }

    public void delegateTo(Completer completer) {
        this.delegate = completer;
    }
}
