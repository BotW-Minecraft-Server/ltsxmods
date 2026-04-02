package link.botwmcs.core.service.tty.console;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jline.reader.Candidate;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.LineReader;
import org.jline.reader.impl.CompletionMatcherImpl;

public final class MinecraftCompletionMatcher extends CompletionMatcherImpl {
    @Override
    protected void defaultMatchers(
            Map<LineReader.Option, Boolean> options,
            boolean prefix,
            CompletingParsedLine line,
            boolean caseInsensitive,
            int errors,
            String originalGroupName
    ) {
        super.defaultMatchers(options, prefix, line, caseInsensitive, errors, originalGroupName);
        this.matchers.addFirst(matches -> {
            final Map<String, List<Candidate>> candidates = new HashMap<>();
            for (Map.Entry<String, List<Candidate>> entry : matches.entrySet()) {
                boolean allMinecraftCandidates = true;
                for (Candidate candidate : entry.getValue()) {
                    if (!(candidate instanceof MinecraftCommandCompleter.MinecraftCandidate)) {
                        allMinecraftCandidates = false;
                        break;
                    }
                }
                if (allMinecraftCandidates) {
                    candidates.put(entry.getKey(), entry.getValue());
                }
            }
            return candidates;
        });
    }
}
