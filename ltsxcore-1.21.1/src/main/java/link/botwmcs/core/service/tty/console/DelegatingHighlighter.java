package link.botwmcs.core.service.tty.console;

import java.util.regex.Pattern;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public final class DelegatingHighlighter implements Highlighter {
    private volatile Highlighter delegate;

    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        final Highlighter current = this.delegate;
        if (current != null) {
            return current.highlight(reader, buffer);
        }
        return new AttributedStringBuilder()
                .append(buffer, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                .toAttributedString();
    }

    @Override
    public void setErrorPattern(Pattern errorPattern) {
        final Highlighter current = this.delegate;
        if (current != null) {
            current.setErrorPattern(errorPattern);
        }
    }

    @Override
    public void setErrorIndex(int errorIndex) {
        final Highlighter current = this.delegate;
        if (current != null) {
            current.setErrorIndex(errorIndex);
        }
    }

    public void delegateTo(Highlighter highlighter) {
        this.delegate = highlighter;
    }
}
