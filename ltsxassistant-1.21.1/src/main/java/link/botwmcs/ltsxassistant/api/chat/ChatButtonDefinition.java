package link.botwmcs.ltsxassistant.api.chat;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/**
 * Definition for one chat tab button.
 */
public record ChatButtonDefinition(
        String id,
        Component label,
        @Nullable String targetPageId,
        int order,
        ChatButtonStyle style,
        Predicate<ChatButtonVisibilityContext> visiblePredicate,
        Consumer<ChatButtonActionContext> onPress
) {
    private static final Predicate<ChatButtonVisibilityContext> ALWAYS_VISIBLE = context -> true;
    private static final Consumer<ChatButtonActionContext> NOOP_ACTION = context -> {
    };

    public ChatButtonDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        style = style == null ? ChatButtonStyle.DEFAULT : style;
        visiblePredicate = visiblePredicate == null ? ALWAYS_VISIBLE : visiblePredicate;
        onPress = onPress == null ? NOOP_ACTION : onPress;
    }

    public static Builder builder(String id, Component label) {
        return new Builder(id, label);
    }

    public boolean isVisible(ChatButtonVisibilityContext context) {
        return this.visiblePredicate.test(context);
    }

    public void press(ChatButtonActionContext context) {
        this.onPress.accept(context);
    }

    public static final class Builder {
        private final String id;
        private final Component label;
        @Nullable
        private String targetPageId;
        private int order;
        private ChatButtonStyle style = ChatButtonStyle.DEFAULT;
        private Predicate<ChatButtonVisibilityContext> visiblePredicate = ALWAYS_VISIBLE;
        private Consumer<ChatButtonActionContext> onPress = NOOP_ACTION;

        private Builder(String id, Component label) {
            this.id = Objects.requireNonNull(id, "id");
            this.label = Objects.requireNonNull(label, "label");
        }

        public Builder targetPageId(@Nullable String targetPageId) {
            this.targetPageId = targetPageId;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder style(ChatButtonStyle style) {
            this.style = style == null ? ChatButtonStyle.DEFAULT : style;
            return this;
        }

        public Builder visibleWhen(Predicate<ChatButtonVisibilityContext> visiblePredicate) {
            this.visiblePredicate = visiblePredicate == null ? ALWAYS_VISIBLE : visiblePredicate;
            return this;
        }

        public Builder onPress(Consumer<ChatButtonActionContext> onPress) {
            this.onPress = onPress == null ? NOOP_ACTION : onPress;
            return this;
        }

        public ChatButtonDefinition build() {
            return new ChatButtonDefinition(id, label, targetPageId, order, style, visiblePredicate, onPress);
        }
    }
}
