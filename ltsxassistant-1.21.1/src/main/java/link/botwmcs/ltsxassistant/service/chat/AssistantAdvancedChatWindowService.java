package link.botwmcs.ltsxassistant.service.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.api.chat.AdvancedChatUiRegistry;
import link.botwmcs.ltsxassistant.api.chat.AdvancedChatWindowService;
import link.botwmcs.ltsxassistant.api.chat.ChatButtonActionContext;
import link.botwmcs.ltsxassistant.api.chat.ChatButtonDefinition;
import link.botwmcs.ltsxassistant.api.chat.ChatButtonStyle;
import link.botwmcs.ltsxassistant.api.chat.ChatPageDefinition;
import link.botwmcs.ltsxassistant.client.elements.DarkPanelElement;
import link.botwmcs.ltsxassistant.client.elements.ShiftedGlobalChatElement;
import net.minecraft.network.chat.Component;

/**
 * Default advanced chat UI service with mutable API-driven tabs/pages registry.
 */
public final class AssistantAdvancedChatWindowService implements AdvancedChatWindowService {
    private static final int PANEL_INNER_COLOR = 0xE5161A23;
    private static final int PANEL_CENTER_COLOR = 0xCC10141C;
    private static final Comparator<ChatButtonDefinition> BUTTON_ORDER = Comparator
            .comparingInt(ChatButtonDefinition::order)
            .thenComparing(ChatButtonDefinition::id);

    private final MutableUiRegistry uiRegistry = new MutableUiRegistry();

    public AssistantAdvancedChatWindowService() {
        bootstrapDefaults();
    }

    @Override
    public AdvancedChatUiRegistry uiRegistry() {
        return this.uiRegistry;
    }

    private void bootstrapDefaults() {
        uiRegistry.upsertPage(
                ChatPageDefinition.builder(DEFAULT_CHAT_PAGE_ID)
                        .fill(ShiftedGlobalChatElement::new)
                        .build()
        );
        uiRegistry.upsertPage(placeholderPage(DEFAULT_GROUP_PAGE_ID, "Group", "Group page placeholder"));
        uiRegistry.upsertPage(placeholderPage(DEFAULT_AGENT_PAGE_ID, "Agent", "Agent page placeholder"));
        uiRegistry.upsertPage(placeholderPage(DEFAULT_ADMIN_PAGE_ID, "Admin", "Admin page placeholder"));
        uiRegistry.setActivePageId(DEFAULT_CHAT_PAGE_ID);

        uiRegistry.upsertButton(ChatButtonDefinition.builder("chat", Component.literal("Chat"))
                .targetPageId(DEFAULT_CHAT_PAGE_ID)
                .order(10)
                .style(ChatButtonStyle.DEFAULT)
                .onPress(logClick("chat"))
                .build());
        uiRegistry.upsertButton(ChatButtonDefinition.builder("group", Component.literal("Group"))
                .targetPageId(DEFAULT_GROUP_PAGE_ID)
                .order(20)
                .style(new ChatButtonStyle(0xFF5F6680, 0xCC252F4D, 0xFFE8EDFF, 0xFF96A0BC))
                .onPress(logClick("group"))
                .build());
        uiRegistry.upsertButton(ChatButtonDefinition.builder("agent", Component.literal("Agent"))
                .targetPageId(DEFAULT_AGENT_PAGE_ID)
                .order(30)
                .style(new ChatButtonStyle(0xFF4A6E63, 0xCC1D3E34, 0xFFE4FFF2, 0xFF8CB8A4))
                .onPress(logClick("agent"))
                .build());
        uiRegistry.upsertButton(ChatButtonDefinition.builder("admin", Component.literal("Admin"))
                .targetPageId(DEFAULT_ADMIN_PAGE_ID)
                .order(40)
                .style(new ChatButtonStyle(0xFF815A56, 0xCC4A2522, 0xFFFFEFEA, 0xFFC6A6A2))
                .visibleWhen(adminVisible())
                .onPress(logClick("admin"))
                .build());
    }

    private static ChatPageDefinition placeholderPage(String id, String title, String description) {
        return ChatPageDefinition.builder(id)
                .fill(() -> new DarkPanelElement(PANEL_INNER_COLOR, PANEL_CENTER_COLOR))
                .fill(() -> new FizzyComponentElement.Builder()
                        .addText(Component.literal(title))
                        .addText(Component.literal(description))
                        .align(TextRenderer.Align.CENTER)
                        .wrap(true)
                        .shadow(true)
                        .color(0xFFE7ECFF)
                        .clipToPad(true)
                        .allowOverflow(false)
                        .build())
                .build();
    }

    private static Consumer<ChatButtonActionContext> logClick(String key) {
        return context -> LTSXAssistant.LOGGER.info(
                "[ltsxassistant] Advanced chat tab clicked: {}",
                key.toLowerCase(Locale.ROOT)
        );
    }

    private Predicate<link.botwmcs.ltsxassistant.api.chat.ChatButtonVisibilityContext> adminVisible() {
        return context -> context.playerId() != null && hasLuckPermsPermission(context.playerId(), ADMIN_PERMISSION_NODE);
    }

    private boolean hasLuckPermsPermission(UUID playerId, String permissionNode) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            if (luckPerms == null) {
                return false;
            }

            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            if (userManager == null) {
                return false;
            }

            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, playerId);
            if (user == null) {
                return false;
            }

            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            if (cachedData == null) {
                return false;
            }

            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            if (permissionData == null) {
                return false;
            }

            Object tristate = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permissionNode);
            if (tristate == null) {
                return false;
            }

            Object asBoolean = tristate.getClass().getMethod("asBoolean").invoke(tristate);
            return asBoolean instanceof Boolean bool && bool;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class MutableUiRegistry implements AdvancedChatUiRegistry {
        private final AtomicLong version = new AtomicLong(1L);
        private final Map<String, ChatButtonDefinition> buttons = new LinkedHashMap<>();
        private final Map<String, ChatPageDefinition> pages = new LinkedHashMap<>();
        private volatile String activePageId = AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID;

        @Override
        public long version() {
            return version.get();
        }

        @Override
        public synchronized List<ChatButtonDefinition> listButtons() {
            List<ChatButtonDefinition> list = new ArrayList<>(buttons.values());
            list.sort(BUTTON_ORDER);
            return List.copyOf(list);
        }

        @Override
        public synchronized Optional<ChatButtonDefinition> getButton(String id) {
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(buttons.get(id));
        }

        @Override
        public synchronized void upsertButton(ChatButtonDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            ChatButtonDefinition previous = buttons.put(definition.id(), definition);
            if (!definition.equals(previous)) {
                version.incrementAndGet();
            }
        }

        @Override
        public synchronized boolean removeButton(String id) {
            if (id == null || id.isBlank()) {
                return false;
            }
            ChatButtonDefinition removed = buttons.remove(id);
            if (removed != null) {
                version.incrementAndGet();
                return true;
            }
            return false;
        }

        @Override
        public synchronized List<ChatPageDefinition> listPages() {
            return List.copyOf(pages.values());
        }

        @Override
        public synchronized Optional<ChatPageDefinition> getPage(String id) {
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(pages.get(id));
        }

        @Override
        public synchronized void upsertPage(ChatPageDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            ChatPageDefinition previous = pages.put(definition.id(), definition);
            if (!definition.equals(previous)) {
                version.incrementAndGet();
            }
            if (activePageId == null || activePageId.isBlank() || !pages.containsKey(activePageId)) {
                activePageId = definition.id();
            }
        }

        @Override
        public synchronized boolean removePage(String id) {
            if (id == null || id.isBlank()) {
                return false;
            }
            ChatPageDefinition removed = pages.remove(id);
            if (removed == null) {
                return false;
            }
            if (id.equals(activePageId)) {
                activePageId = pages.isEmpty()
                        ? AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID
                        : pages.keySet().iterator().next();
            }
            version.incrementAndGet();
            return true;
        }

        @Override
        public String activePageId() {
            return activePageId;
        }

        @Override
        public synchronized void setActivePageId(String pageId) {
            if (pageId == null || pageId.isBlank()) {
                return;
            }
            if (!pages.containsKey(pageId)) {
                return;
            }
            activePageId = pageId;
        }
    }
}
