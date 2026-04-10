package link.botwmcs.ltsxassistant.api.chat;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Runtime context used to evaluate button visibility.
 */
public record ChatButtonVisibilityContext(
        AdvancedChatUiRegistry registry,
        @Nullable UUID playerId,
        int permissionLevel,
        String activePageId
) {
}
