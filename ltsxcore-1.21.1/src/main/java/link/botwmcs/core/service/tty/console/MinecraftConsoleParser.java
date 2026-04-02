package link.botwmcs.core.service.tty.console;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.dedicated.DedicatedServer;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;

public record MinecraftConsoleParser(DedicatedServer server) implements Parser {
    @Override
    public ParsedLine parse(String line, int cursor, ParseContext context) throws SyntaxError {
        final ParseResults<CommandSourceStack> results =
                this.server.getCommands().getDispatcher().parse(new StringReader(line), this.server.createCommandSourceStack());
        final ImmutableStringReader reader = results.getReader();
        final List<String> words = new ArrayList<>();

        CommandContextBuilder<CommandSourceStack> currentContext = results.getContext();
        int currentWordIndex = -1;
        int wordIndex = -1;
        int inWordCursor = -1;

        if (currentContext.getRange().getLength() > 0) {
            do {
                for (ParsedCommandNode<CommandSourceStack> node : currentContext.getNodes()) {
                    final StringRange nodeRange = node.getRange();
                    words.add(nodeRange.get(reader));
                    currentWordIndex++;
                    if (wordIndex == -1 && nodeRange.getStart() <= cursor && nodeRange.getEnd() >= cursor) {
                        wordIndex = currentWordIndex;
                        inWordCursor = cursor - nodeRange.getStart();
                    }
                }
                currentContext = currentContext.getChild();
            } while (currentContext != null);
        }

        final String leftovers = reader.getRemaining();
        if (!leftovers.isEmpty() && leftovers.isBlank()) {
            currentWordIndex++;
            words.add("");
            if (wordIndex == -1) {
                wordIndex = currentWordIndex;
                inWordCursor = 0;
            }
        } else if (!leftovers.isEmpty()) {
            currentWordIndex++;
            words.add(leftovers);
            if (wordIndex == -1) {
                wordIndex = currentWordIndex;
                inWordCursor = cursor - reader.getCursor();
            }
        }

        if (wordIndex == -1) {
            currentWordIndex++;
            words.add("");
            wordIndex = currentWordIndex;
            inWordCursor = 0;
        }

        return new BrigadierParsedLine(words.get(wordIndex), inWordCursor, wordIndex, words, line, cursor);
    }

    public record BrigadierParsedLine(
            String word,
            int wordCursor,
            int wordIndex,
            List<String> words,
            String line,
            int cursor
    ) implements ParsedLine {
    }
}
