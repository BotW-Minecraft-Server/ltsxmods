package link.botwmcs.ltsxassistant.api.chat;

import java.util.List;
import java.util.Optional;

/**
 * Mutable registry for advanced chat tabs and pages.
 */
public interface AdvancedChatUiRegistry {
    long version();

    List<ChatButtonDefinition> listButtons();

    Optional<ChatButtonDefinition> getButton(String id);

    void upsertButton(ChatButtonDefinition definition);

    boolean removeButton(String id);

    List<ChatPageDefinition> listPages();

    Optional<ChatPageDefinition> getPage(String id);

    void upsertPage(ChatPageDefinition definition);

    boolean removePage(String id);

    String activePageId();

    void setActivePageId(String pageId);
}
