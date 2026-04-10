package link.botwmcs.ltsxassistant.api.chat;

import java.util.Objects;

/**
 * Positioning metadata for one page element.
 */
public record ChatPageElementDefinition(
        int x,
        int y,
        int width,
        int height,
        boolean fillWidth,
        boolean fillHeight,
        ChatPageElementFactory factory
) {
    public ChatPageElementDefinition {
        Objects.requireNonNull(factory, "factory");
    }

    public static ChatPageElementDefinition fill(ChatPageElementFactory factory) {
        return new ChatPageElementDefinition(0, 0, 0, 0, true, true, factory);
    }

    public static ChatPageElementDefinition byPx(int x, int y, int width, int height, ChatPageElementFactory factory) {
        return new ChatPageElementDefinition(x, y, Math.max(1, width), Math.max(1, height), false, false, factory);
    }
}
