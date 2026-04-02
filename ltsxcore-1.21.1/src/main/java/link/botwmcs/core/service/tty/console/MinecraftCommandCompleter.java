package link.botwmcs.core.service.tty.console;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

public record MinecraftCommandCompleter(MinecraftServer server) implements Completer {
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        final StringReader stringReader = TtyConsoleUtil.prepareStringReader(line.line());
        final ParseResults<CommandSourceStack> results =
                this.server.getCommands().getDispatcher().parse(stringReader, this.server.createCommandSourceStack());
        final CompletableFuture<Suggestions> suggestionsFuture =
                this.server.getCommands().getDispatcher().getCompletionSuggestions(results, line.cursor());
        final Suggestions suggestions = suggestionsFuture.join();

        final ParseContext parseContext =
                new ParseContext(line.line(), results.getContext().findSuggestionContext(line.cursor()).startPos);
        for (Suggestion suggestion : suggestions.getList()) {
            final String suggestionText = suggestion.getText();
            if (suggestionText.isEmpty()) {
                continue;
            }
            candidates.add(this.toCandidate(suggestion, parseContext));
        }
    }

    private Candidate toCandidate(Suggestion suggestion, ParseContext context) {
        return this.toCandidate(
                context.line.substring(context.suggestionStart, suggestion.getRange().getStart()) + suggestion.getText(),
                suggestion.getTooltip()
        );
    }

    private Candidate toCandidate(String suggestionText, Message descriptionMessage) {
        final String description = Optional.ofNullable(descriptionMessage)
                .map(ComponentUtils::fromMessage)
                .map(component -> component.getString())
                .filter(text -> !text.isBlank())
                .orElse(null);

        return new MinecraftCandidate(
                suggestionText,
                suggestionText,
                null,
                description,
                null,
                null,
                false
        );
    }

    private record ParseContext(String line, int suggestionStart) {
    }

    public static final class MinecraftCandidate extends Candidate {
        public MinecraftCandidate(
                String value,
                String display,
                String group,
                String description,
                String suffix,
                String key,
                boolean complete
        ) {
            super(value, display, group, description, suffix, key, complete);
        }
    }
}
