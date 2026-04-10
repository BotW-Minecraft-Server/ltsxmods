package link.botwmcs.ltsxassistant.api.chat;

import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;

/**
 * Runtime context provided to button click actions.
 */
public interface ChatButtonActionContext {
    AdvancedChatUiRegistry registry();

    @Nullable
    UUID playerId();

    int permissionLevel();

    String activePageId();

    void setActivePageId(String pageId);

    void openScreen(Screen screen);

    void closeScreen();

    void openFile(Path path);

    void openUri(String uri);

    default void upsertPage(ChatPageDefinition pageDefinition) {
        registry().upsertPage(pageDefinition);
    }

    default boolean removePage(String pageId) {
        return registry().removePage(pageId);
    }

    default void upsertButton(ChatButtonDefinition buttonDefinition) {
        registry().upsertButton(buttonDefinition);
    }

    default boolean removeButton(String buttonId) {
        return registry().removeButton(buttonId);
    }
}
