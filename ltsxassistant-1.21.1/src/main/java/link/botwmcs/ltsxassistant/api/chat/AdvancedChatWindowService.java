package link.botwmcs.ltsxassistant.api.chat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/**
 * Service contract for assistant advanced chat window behavior.
 */
public interface AdvancedChatWindowService {
    String ADMIN_PERMISSION_NODE = "ltsxassistant.chat.admin";

    List<ChatButtonSpec> resolveButtons(@Nullable UUID playerId, int permissionLevel);

    void onButtonPressed(ChatButton button);

    enum ChatButton {
        CHAT,
        GROUP,
        AGENT,
        ADMIN
    }

    record ChatButtonSpec(ChatButton button, Component label) {
        public ChatButtonSpec {
            Objects.requireNonNull(button, "button");
            Objects.requireNonNull(label, "label");
        }
    }
}
