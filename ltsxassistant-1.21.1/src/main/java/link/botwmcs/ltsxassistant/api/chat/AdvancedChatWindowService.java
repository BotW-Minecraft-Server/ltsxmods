package link.botwmcs.ltsxassistant.api.chat;

/**
 * Service contract for assistant advanced chat window behavior.
 */
public interface AdvancedChatWindowService {
    String ADMIN_PERMISSION_NODE = "ltsxassistant.chat.admin";
    String DEFAULT_CHAT_PAGE_ID = "chat";
    String DEFAULT_GROUP_PAGE_ID = "group";
    String DEFAULT_AGENT_PAGE_ID = "agent";
    String DEFAULT_ADMIN_PAGE_ID = "admin";

    AdvancedChatUiRegistry uiRegistry();
}
