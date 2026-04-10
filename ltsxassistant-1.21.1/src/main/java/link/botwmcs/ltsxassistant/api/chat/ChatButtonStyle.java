package link.botwmcs.ltsxassistant.api.chat;

/**
 * Visual style for {@code BadgeButtonElement} tab buttons.
 */
public record ChatButtonStyle(
        int outlineColor,
        int fillColor,
        int textColor,
        int disabledTextColor
) {
    public static final ChatButtonStyle DEFAULT = new ChatButtonStyle(
            0xFF5A5A5A,
            0xCC2B2B2B,
            0xFFFFFFFF,
            0xFF9A9A9A
    );
}
