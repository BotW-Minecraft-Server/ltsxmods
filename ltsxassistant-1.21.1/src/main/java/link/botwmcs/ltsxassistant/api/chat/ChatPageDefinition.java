package link.botwmcs.ltsxassistant.api.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Definition for one content page rendered in the swappable content area.
 */
public record ChatPageDefinition(String id, List<ChatPageElementDefinition> elements) {
    public ChatPageDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(elements, "elements");
        elements = List.copyOf(elements);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private final List<ChatPageElementDefinition> elements = new ArrayList<>();

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder element(ChatPageElementDefinition definition) {
            if (definition != null) {
                this.elements.add(definition);
            }
            return this;
        }

        public Builder fill(ChatPageElementFactory factory) {
            if (factory != null) {
                this.elements.add(ChatPageElementDefinition.fill(factory));
            }
            return this;
        }

        public Builder byPx(int x, int y, int width, int height, ChatPageElementFactory factory) {
            if (factory != null) {
                this.elements.add(ChatPageElementDefinition.byPx(x, y, width, height, factory));
            }
            return this;
        }

        public ChatPageDefinition build() {
            return new ChatPageDefinition(id, List.copyOf(elements));
        }
    }
}
