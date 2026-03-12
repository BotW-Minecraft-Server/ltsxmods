package link.botwmcs.core.command;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

final class LtsxCommandTextFormatter {
    private static final ChatFormatting TITLE_COLOR = ChatFormatting.YELLOW;
    private static final ChatFormatting LABEL_COLOR = ChatFormatting.GRAY;
    private static final ChatFormatting VALUE_COLOR = ChatFormatting.WHITE;

    private LtsxCommandTextFormatter() {
    }

    static Component helpHeader(String text) {
        return Component.literal(text).withStyle(TITLE_COLOR);
    }

    static Component helpLine(String commandPath, Component description) {
        return Component.empty()
                .append(Component.literal("- ").withStyle(LABEL_COLOR))
                .append(Component.literal(commandPath).withStyle(VALUE_COLOR))
                .append(Component.literal(" -> ").withStyle(LABEL_COLOR))
                .append(description.copy().withStyle(VALUE_COLOR));
    }

    static Component emptyLine() {
        return Component.literal("- <none>").withStyle(LABEL_COLOR);
    }

    static String commandPath(List<String> path) {
        return "/" + String.join(" ", path);
    }
}
