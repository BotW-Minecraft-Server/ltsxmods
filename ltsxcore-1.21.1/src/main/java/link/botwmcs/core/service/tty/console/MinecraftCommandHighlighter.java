package link.botwmcs.core.service.tty.console;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public record MinecraftCommandHighlighter(
        MinecraftServer server,
        TtyStyleColor[] colors
) implements Highlighter {
    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        final AttributedStringBuilder builder = new AttributedStringBuilder();
        final StringReader stringReader = TtyConsoleUtil.prepareStringReader(buffer);
        final ParseResults<CommandSourceStack> results =
                this.server.getCommands().getDispatcher().parse(stringReader, this.server.createCommandSourceStack());

        int position = 0;
        if (buffer.startsWith("/")) {
            builder.append("/", AttributedStyle.DEFAULT);
            position = 1;
        }

        int colorIndex = -1;
        for (ParsedCommandNode<CommandSourceStack> node : results.getContext().getLastChild().getNodes()) {
            if (node.getRange().getStart() >= buffer.length()) {
                break;
            }

            final int start = node.getRange().getStart();
            final int end = Math.min(node.getRange().getEnd(), buffer.length());
            builder.append(buffer.substring(position, start), AttributedStyle.DEFAULT);
            if (node.getNode() instanceof LiteralCommandNode || this.colors.length == 0) {
                builder.append(buffer.substring(start, end), AttributedStyle.DEFAULT);
            } else {
                colorIndex++;
                if (colorIndex >= this.colors.length) {
                    colorIndex = 0;
                }
                builder.append(buffer.substring(start, end),
                        AttributedStyle.DEFAULT.foreground(this.colors[colorIndex].index()));
            }
            position = end;
        }

        if (position < buffer.length()) {
            builder.append(buffer.substring(position), AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
        }
        return builder.toAttributedString();
    }

    @Override
    public void setErrorPattern(Pattern errorPattern) {
    }

    @Override
    public void setErrorIndex(int errorIndex) {
    }
}
