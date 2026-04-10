package link.botwmcs.ltsxassistant.client.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.fizzy.ui.background.BgPainter;
import link.botwmcs.fizzy.ui.core.FizzyGui;
import link.botwmcs.fizzy.ui.core.FizzyGuiBuilder;
import link.botwmcs.fizzy.ui.core.HostType;
import link.botwmcs.fizzy.ui.frame.FrameMetrics;
import link.botwmcs.fizzy.ui.frame.FramePainter;
import link.botwmcs.fizzy.ui.host.FizzyScreenHost;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.api.chat.AdvancedChatUiRegistry;
import link.botwmcs.ltsxassistant.api.chat.AdvancedChatWindowService;
import link.botwmcs.ltsxassistant.api.chat.ChatButtonActionContext;
import link.botwmcs.ltsxassistant.api.chat.ChatButtonDefinition;
import link.botwmcs.ltsxassistant.api.chat.ChatButtonStyle;
import link.botwmcs.ltsxassistant.api.chat.ChatButtonVisibilityContext;
import link.botwmcs.ltsxassistant.api.chat.ChatPageDefinition;
import link.botwmcs.ltsxassistant.api.chat.ChatPageElementDefinition;
import link.botwmcs.ltsxassistant.client.elements.BadgeButtonElement;
import link.botwmcs.ltsxassistant.client.elements.ShiftedGlobalChatElement;
import link.botwmcs.ltsxassistant.client.elements.SwappableContentElement;
import link.botwmcs.ltsxassistant.service.chat.AssistantAdvancedChatWindowService;
import net.minecraft.Util;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * Fizzy-hosted assistant chat screen with a tabbed content area and API-driven buttons/pages.
 */
public final class LtsxChatScreen extends FizzyScreenHost {
    public static final double MOUSE_SCROLL_SPEED = 7.0;
    private static final Component TITLE = Component.translatable("chat_screen.title");
    private static final Component USAGE_TEXT = Component.translatable("chat_screen.usage");
    private static final int TOOLTIP_MAX_WIDTH = 210;

    private static final int CHAT_CONTENT_SHIFT_UP_PX = 12;
    private static final int CONTENT_BUTTON_GAP_PX = 4;
    private static final int BUTTON_INPUT_GAP_PX = 2;
    private static final int BUTTON_SIDE_MARGIN_PX = 2;
    private static final int BUTTON_GAP_PX = 4;
    private static final int BUTTON_TEXT_PADDING_PX = 3;
    private static final int BUTTON_ROW_HEIGHT_PX = 14;
    private static final int INPUT_BACKGROUND_TOP_OFFSET_PX = 14;
    private static final int INPUT_BACKGROUND_BOTTOM_OFFSET_PX = 2;
    private static final int MIN_CONTENT_TOP_PX = 2;

    private String historyBuffer = "";
    private int historyPos = -1;
    protected EditBox input;
    private String initial;
    private CommandSuggestions commandSuggestions;

    private final AdvancedChatUiRegistry uiRegistry;
    private final long registryVersion;
    private final ScreenLayout layout;

    public LtsxChatScreen(String initial) {
        this(initial, buildModel(currentWidth(), currentHeight()));
    }

    private LtsxChatScreen(String initial, int width, int height) {
        this(initial, buildModel(width, height));
    }

    private LtsxChatScreen(String initial, ScreenModel model) {
        super(buildGui(model));
        this.initial = Objects.requireNonNullElse(initial, "");
        this.uiRegistry = model.registry();
        this.registryVersion = model.registryVersion();
        this.layout = model.layout();
    }

    @Override
    protected void init() {
        super.init();
        this.historyPos = this.minecraft.gui.getChat().getRecentChat().size();
        this.input = new EditBox(this.minecraft.fontFilterFishy, 4, this.height - 12, this.width - 4, 12, Component.translatable("chat.editBox")) {
            @Override
            protected MutableComponent createNarrationMessage() {
                return super.createNarrationMessage().append(LtsxChatScreen.this.commandSuggestions.getNarrationMessage());
            }
        };
        this.input.setMaxLength(256);
        this.input.setBordered(false);
        this.input.setValue(this.initial);
        this.input.setResponder(this::onEdited);
        this.input.setCanLoseFocus(false);
        this.addWidget(this.input);
        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font, false, false, 1, 10, true, -805306368);
        this.commandSuggestions.setAllowHiding(false);
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.input);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String current = this.input == null ? this.initial : this.input.getValue();
        minecraft.setScreen(new LtsxChatScreen(current, width, height));
    }

    @Override
    public void removed() {
        this.minecraft.gui.getChat().resetChatScroll();
        super.removed();
    }

    private void onEdited(String value) {
        if (this.input == null || this.commandSuggestions == null) {
            return;
        }
        String text = this.input.getValue();
        this.commandSuggestions.setAllowSuggestions(!text.equals(this.initial));
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.maybeRebuildForRegistryChange()) {
            return true;
        }
        if (this.commandSuggestions != null && this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 256) {
            this.minecraft.setScreen(null);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            this.handleChatInput(this.input.getValue(), true);
            if (this.minecraft.screen == this) {
                this.minecraft.setScreen(null);
            }
            return true;
        }
        if (keyCode == 265) {
            this.moveInHistory(-1);
            return true;
        }
        if (keyCode == 264) {
            this.moveInHistory(1);
            return true;
        }
        if (keyCode == 266) {
            this.minecraft.gui.getChat().scrollChat(this.minecraft.gui.getChat().getLinesPerPage() - 1);
            return true;
        }
        if (keyCode == 267) {
            this.minecraft.gui.getChat().scrollChat(-this.minecraft.gui.getChat().getLinesPerPage() + 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.maybeRebuildForRegistryChange()) {
            return true;
        }
        scrollY = Mth.clamp(scrollY, -1.0, 1.0);
        if (this.commandSuggestions != null && this.commandSuggestions.mouseScrolled(scrollY)) {
            return true;
        }
        if (!isGlobalChatPageActive()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (!hasShiftDown()) {
            scrollY *= MOUSE_SCROLL_SPEED;
        }
        this.minecraft.gui.getChat().scrollChat((int) scrollY);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.maybeRebuildForRegistryChange()) {
            return true;
        }
        if (this.commandSuggestions != null && this.commandSuggestions.mouseClicked((double) ((int) mouseX), (double) ((int) mouseY), button)) {
            return true;
        }

        if (button == 0 && isGlobalChatPageActive()) {
            ChatComponent chat = this.minecraft.gui.getChat();
            double chatMouseY = transformMouseYForShiftedChat(mouseY);
            if (chat.handleChatQueueClicked(mouseX, chatMouseY)) {
                return true;
            }

            Style style = this.getComponentStyleAt(mouseX, chatMouseY);
            if (style != null && this.handleComponentClicked(style)) {
                this.initial = this.input.getValue();
                return true;
            }
        }

        if (this.input.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.input.setValue(text);
        } else {
            this.input.insertText(text);
        }
    }

    public void moveInHistory(int msgPos) {
        int nextPos = this.historyPos + msgPos;
        int historySize = this.minecraft.gui.getChat().getRecentChat().size();
        nextPos = Mth.clamp(nextPos, 0, historySize);
        if (nextPos == this.historyPos) {
            return;
        }

        if (nextPos == historySize) {
            this.historyPos = historySize;
            this.input.setValue(this.historyBuffer);
            return;
        }

        if (this.historyPos == historySize) {
            this.historyBuffer = this.input.getValue();
        }

        this.input.setValue(this.minecraft.gui.getChat().getRecentChat().get(nextPos));
        this.commandSuggestions.setAllowSuggestions(false);
        this.historyPos = nextPos;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.maybeRebuildForRegistryChange()) {
            return;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.fill(
                2,
                this.height - INPUT_BACKGROUND_TOP_OFFSET_PX,
                this.width - 2,
                this.height - INPUT_BACKGROUND_BOTTOM_OFFSET_PX,
                this.minecraft.options.getBackgroundColor(Integer.MIN_VALUE)
        );
        this.input.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        if (this.commandSuggestions != null) {
            this.commandSuggestions.render(guiGraphics, mouseX, mouseY);
        }
        guiGraphics.pose().popPose();

        if (!isGlobalChatPageActive()) {
            return;
        }

        double chatMouseY = transformMouseYForShiftedChat(mouseY);
        GuiMessageTag messageTag = this.minecraft.gui.getChat().getMessageTagAt((double) mouseX, chatMouseY);
        if (messageTag != null && messageTag.text() != null) {
            guiGraphics.renderTooltip(this.font, this.font.split(messageTag.text(), TOOLTIP_MAX_WIDTH), mouseX, mouseY);
            return;
        }

        Style style = this.getComponentStyleAt((double) mouseX, chatMouseY);
        if (style != null && style.getHoverEvent() != null) {
            guiGraphics.renderComponentHoverEffect(this.font, style, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, TITLE);
        output.add(NarratedElementType.USAGE, USAGE_TEXT);
        if (this.input == null) {
            return;
        }
        String text = this.input.getValue();
        if (!text.isEmpty()) {
            output.nest().add(NarratedElementType.TITLE, Component.translatable("chat_screen.message", text));
        }
    }

    @Nullable
    private Style getComponentStyleAt(double mouseX, double mouseY) {
        return this.minecraft.gui.getChat().getClickedComponentStyleAt(mouseX, mouseY);
    }

    public void handleChatInput(String message, boolean addToRecentChat) {
        message = this.normalizeChatMessage(message);
        if (message.isEmpty()) {
            return;
        }
        if (addToRecentChat) {
            this.minecraft.gui.getChat().addRecentChat(message);
        }

        if (this.minecraft.player == null || this.minecraft.player.connection == null) {
            return;
        }
        if (message.startsWith("/")) {
            this.minecraft.player.connection.sendCommand(message.substring(1));
        } else {
            this.minecraft.player.connection.sendChat(message);
        }
    }

    public String normalizeChatMessage(String message) {
        return StringUtil.trimChatMessage(StringUtils.normalizeSpace(message.trim()));
    }

    private boolean isGlobalChatPageActive() {
        return AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID.equals(this.uiRegistry.activePageId());
    }

    private double transformMouseYForShiftedChat(double mouseY) {
        return mouseY - chatRenderTranslationY();
    }

    private int chatRenderTranslationY() {
        int contentBottom = this.layout.contentY() + this.layout.contentHeight();
        return ShiftedGlobalChatElement.computeRenderTranslationY(this.height, contentBottom);
    }

    private boolean maybeRebuildForRegistryChange() {
        long currentVersion = this.uiRegistry.version();
        if (currentVersion == this.registryVersion || this.minecraft == null) {
            return false;
        }
        String currentInput = this.input == null ? this.initial : this.input.getValue();
        this.minecraft.setScreen(new LtsxChatScreen(currentInput, this.width, this.height));
        return true;
    }

    private void handleButtonClick(String buttonId) {
        if (buttonId == null || buttonId.isBlank()) {
            return;
        }
        this.uiRegistry.getButton(buttonId).ifPresent(buttonDefinition -> {
            ChatButtonVisibilityContext visibilityContext = createVisibilityContext(this.uiRegistry);
            if (!safeVisible(buttonDefinition, visibilityContext)) {
                return;
            }

            PlayerIdentity identity = resolveIdentity();
            if (buttonDefinition.targetPageId() != null && !buttonDefinition.targetPageId().isBlank()) {
                this.uiRegistry.setActivePageId(buttonDefinition.targetPageId());
            }

            try {
                buttonDefinition.press(new ScreenButtonActionContext(
                        this,
                        this.uiRegistry,
                        identity.uuid(),
                        identity.permissionLevel()
                ));
            } catch (Throwable throwable) {
                LTSXAssistant.LOGGER.warn("Failed to execute advanced chat button action. id={}", buttonId, throwable);
            }
        });
    }

    private static void dispatchButtonClick(String buttonId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof LtsxChatScreen ltsxChatScreen)) {
            return;
        }
        ltsxChatScreen.handleButtonClick(buttonId);
    }

    private static ScreenModel buildModel(int width, int height) {
        AdvancedChatWindowService service = CoreServices.getOptional(AdvancedChatWindowService.class)
                .orElseGet(AssistantAdvancedChatWindowService::new);
        AdvancedChatUiRegistry registry = service.uiRegistry();

        if (registry.getPage(AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID).isEmpty()) {
            registry.upsertPage(ChatPageDefinition.builder(AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID)
                    .fill(ShiftedGlobalChatElement::new)
                    .build());
        }
        if (registry.activePageId() == null || registry.activePageId().isBlank()) {
            registry.setActivePageId(AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID);
        }

        ScreenLayout layout = ScreenLayout.compute(width, height);
        Map<String, List<ChatPageElementDefinition>> pages = materializePages(registry);
        List<ChatButtonDefinition> buttons = visibleButtons(registry);
        long version = registry.version();
        return new ScreenModel(service, registry, version, layout, pages, buttons);
    }

    private static Map<String, List<ChatPageElementDefinition>> materializePages(AdvancedChatUiRegistry registry) {
        Map<String, List<ChatPageElementDefinition>> out = new LinkedHashMap<>();
        for (ChatPageDefinition pageDefinition : registry.listPages()) {
            if (pageDefinition == null || pageDefinition.id() == null || pageDefinition.id().isBlank()) {
                continue;
            }
            out.put(pageDefinition.id(), List.copyOf(pageDefinition.elements()));
        }
        if (out.isEmpty()) {
            out.put(
                    AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID,
                    List.of(ChatPageElementDefinition.fill(ShiftedGlobalChatElement::new))
            );
        }
        return out;
    }

    private static List<ChatButtonDefinition> visibleButtons(AdvancedChatUiRegistry registry) {
        ChatButtonVisibilityContext visibilityContext = createVisibilityContext(registry);
        List<ChatButtonDefinition> visible = new ArrayList<>();
        for (ChatButtonDefinition buttonDefinition : registry.listButtons()) {
            if (buttonDefinition == null) {
                continue;
            }
            if (safeVisible(buttonDefinition, visibilityContext)) {
                visible.add(buttonDefinition);
            }
        }
        if (visible.isEmpty()) {
            visible.add(ChatButtonDefinition.builder("chat", Component.literal("Chat"))
                    .targetPageId(AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID)
                    .style(ChatButtonStyle.DEFAULT)
                    .build());
        }
        return List.copyOf(visible);
    }

    private static boolean safeVisible(ChatButtonDefinition buttonDefinition, ChatButtonVisibilityContext context) {
        try {
            return buttonDefinition.isVisible(context);
        } catch (Throwable throwable) {
            LTSXAssistant.LOGGER.warn("Failed to evaluate advanced chat button visibility. id={}", buttonDefinition.id(), throwable);
            return false;
        }
    }

    private static ChatButtonVisibilityContext createVisibilityContext(AdvancedChatUiRegistry registry) {
        PlayerIdentity identity = resolveIdentity();
        return new ChatButtonVisibilityContext(
                registry,
                identity.uuid(),
                identity.permissionLevel(),
                Objects.requireNonNullElse(registry.activePageId(), "")
        );
    }

    private static FizzyGui buildGui(ScreenModel model) {
        SwappableContentElement contentElement = new SwappableContentElement(
                model.pagesById(),
                model.registry()::activePageId,
                AdvancedChatWindowService.DEFAULT_CHAT_PAGE_ID
        );
        ScreenLayout layout = model.layout();

        FizzyGuiBuilder builder = FizzyGuiBuilder.start()
                .sizeSlots(1, 1)
                .host(HostType.SCREEN)
                .frame(new ViewportFramePainter())
                .background(EmptyBackgroundPainter.INSTANCE)
                .overrideSizePx(layout.screenWidth(), layout.screenHeight());

        builder.padByPx(layout.contentX(), layout.contentY(), layout.contentWidth(), layout.contentHeight())
                .element(contentElement)
                .done();
        addButtons(builder, layout, model.visibleButtons());
        return builder.build();
    }

    private static void addButtons(FizzyGuiBuilder builder, ScreenLayout layout, List<ChatButtonDefinition> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int cursorX = layout.buttonX() + BUTTON_SIDE_MARGIN_PX;
        int rowY = layout.buttonY();
        int maxRight = layout.buttonX() + layout.buttonWidth() - BUTTON_SIDE_MARGIN_PX;

        for (ChatButtonDefinition definition : buttons) {
            if (definition == null) {
                continue;
            }
            int tagWidth = Math.max(24, minecraft.font.width(definition.label()) + BUTTON_TEXT_PADDING_PX * 2);
            int padWidth = tagWidth + 1;
            if (cursorX + padWidth > maxRight) {
                break;
            }

            ChatButtonStyle style = definition.style() == null ? ChatButtonStyle.DEFAULT : definition.style();
            String buttonId = definition.id();
            builder.padByPx(cursorX, rowY, padWidth, BUTTON_ROW_HEIGHT_PX)
                    .element(BadgeButtonElement.builder(() -> dispatchButtonClick(buttonId))
                            .text(definition.label())
                            .tagWidthPx(tagWidth)
                            .outlineColor(style.outlineColor())
                            .fillColor(style.fillColor())
                            .textColor(style.textColor())
                            .disabledTextColor(style.disabledTextColor())
                            .build())
                    .done();
            cursorX += padWidth + BUTTON_GAP_PX;
        }
    }

    private static int currentWidth() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 320 : minecraft.getWindow().getGuiScaledWidth();
    }

    private static int currentHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 240 : minecraft.getWindow().getGuiScaledHeight();
    }

    private static PlayerIdentity resolveIdentity() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        return player == null
                ? new PlayerIdentity(null, 0)
                : new PlayerIdentity(player.getUUID(), player.getPermissionLevel());
    }

    private record PlayerIdentity(@Nullable UUID uuid, int permissionLevel) {
    }

    private record ScreenModel(
            AdvancedChatWindowService service,
            AdvancedChatUiRegistry registry,
            long registryVersion,
            ScreenLayout layout,
            Map<String, List<ChatPageElementDefinition>> pagesById,
            List<ChatButtonDefinition> visibleButtons
    ) {
    }

    private record ScreenLayout(
            int screenWidth,
            int screenHeight,
            int contentX,
            int contentY,
            int contentWidth,
            int contentHeight,
            int buttonX,
            int buttonY,
            int buttonWidth,
            int buttonHeight
    ) {
        private static ScreenLayout compute(int width, int height) {
            Minecraft minecraft = Minecraft.getInstance();
            int maxContentWidth = Math.max(1, width - 4);
            int contentWidth = maxContentWidth;
            int contentHeight = Math.max(40, height / 3);
            if (minecraft != null && minecraft.gui != null && minecraft.gui.getChat() != null) {
                ChatComponent chat = minecraft.gui.getChat();
                contentWidth = Mth.clamp(Mth.ceil(chat.getWidth()) + 8, 1, maxContentWidth);
                contentHeight = Mth.clamp(
                        Mth.ceil(chat.getHeight() * chat.getScale()),
                        40,
                        Math.max(40, height - 20)
                );
            }

            int inputTop = height - INPUT_BACKGROUND_TOP_OFFSET_PX;
            int buttonBottomLimit = inputTop - BUTTON_INPUT_GAP_PX;
            int defaultContentBottom = height - 40 - CHAT_CONTENT_SHIFT_UP_PX;
            int maxContentBottom = buttonBottomLimit - BUTTON_ROW_HEIGHT_PX - CONTENT_BUTTON_GAP_PX;
            int contentBottom = Math.min(defaultContentBottom, maxContentBottom);
            int contentY = contentBottom - contentHeight;
            if (contentY < MIN_CONTENT_TOP_PX) {
                contentY = MIN_CONTENT_TOP_PX;
                contentHeight = Math.max(1, contentBottom - contentY);
            }

            int buttonY = contentBottom + CONTENT_BUTTON_GAP_PX;
            if (buttonY + BUTTON_ROW_HEIGHT_PX > buttonBottomLimit) {
                buttonY = buttonBottomLimit - BUTTON_ROW_HEIGHT_PX;
            }

            return new ScreenLayout(
                    width,
                    height,
                    2,
                    contentY,
                    contentWidth,
                    contentHeight,
                    2,
                    Math.max(0, buttonY),
                    contentWidth,
                    BUTTON_ROW_HEIGHT_PX
            );
        }
    }

    private static final class ScreenButtonActionContext implements ChatButtonActionContext {
        private final LtsxChatScreen screen;
        private final AdvancedChatUiRegistry registry;
        @Nullable
        private final UUID playerId;
        private final int permissionLevel;

        private ScreenButtonActionContext(
                LtsxChatScreen screen,
                AdvancedChatUiRegistry registry,
                @Nullable UUID playerId,
                int permissionLevel
        ) {
            this.screen = screen;
            this.registry = registry;
            this.playerId = playerId;
            this.permissionLevel = permissionLevel;
        }

        @Override
        public AdvancedChatUiRegistry registry() {
            return registry;
        }

        @Override
        @Nullable
        public UUID playerId() {
            return playerId;
        }

        @Override
        public int permissionLevel() {
            return permissionLevel;
        }

        @Override
        public String activePageId() {
            return Objects.requireNonNullElse(registry.activePageId(), "");
        }

        @Override
        public void setActivePageId(String pageId) {
            registry.setActivePageId(pageId);
        }

        @Override
        public void openScreen(Screen screen) {
            if (screen == null || this.screen.minecraft == null) {
                return;
            }
            this.screen.minecraft.setScreen(screen);
        }

        @Override
        public void closeScreen() {
            if (this.screen.minecraft == null) {
                return;
            }
            this.screen.minecraft.setScreen(null);
        }

        @Override
        public void openFile(Path path) {
            if (path == null) {
                return;
            }
            Util.getPlatform().openFile(path.toFile());
        }

        @Override
        public void openUri(String uri) {
            if (uri == null || uri.isBlank()) {
                return;
            }
            Util.getPlatform().openUri(uri);
        }
    }

    private static final class ViewportFramePainter implements FramePainter {
        private static final FrameMetrics METRICS = new FrameMetrics() {
            @Override
            public int texW() {
                return 0;
            }

            @Override
            public int texH() {
                return 0;
            }

            @Override
            public int panelW() {
                return 0;
            }

            @Override
            public int titleStartH() {
                return 0;
            }

            @Override
            public int slotStartTopPx() {
                return 0;
            }

            @Override
            public int slotStartLeftPx() {
                return 0;
            }

            @Override
            public int slotSizePx() {
                return 0;
            }

            @Override
            public int slotInnerStartY() {
                return 0;
            }

            @Override
            public int slotInnerHeight() {
                return 0;
            }

            @Override
            public int topBorderY() {
                return 0;
            }

            @Override
            public int bottomBorderY() {
                return 0;
            }

            @Override
            public int bottomPadStartY() {
                return 0;
            }

            @Override
            public int bottomPadHeight() {
                return 0;
            }

            @Override
            public int bottomEdgeStartY() {
                return 0;
            }

            @Override
            public int bottomEdgeHeight() {
                return 0;
            }

            @Override
            public int buttomInvExtraStartY() {
                return 0;
            }

            @Override
            public int buttomInvExtraHeight() {
                return 0;
            }

            @Override
            public int totalHeightForRows(int rows, boolean screen, boolean below) {
                return 0;
            }

            @Override
            public int totalWidthForCols(int cols) {
                return 0;
            }
        };

        private Layout layout = new Layout(0, 0, 0, 0, false, false);

        @Override
        public void paint(GuiGraphics guiGraphics, int left, int top, int width, int height, boolean drawBottomEdge, boolean hasBelow) {
        }

        @Override
        public FrameMetrics metrics() {
            return METRICS;
        }

        @Override
        public void setLayout(int left, int top, int width, int height, boolean drawBottomEdge, boolean hasBelow) {
            layout = new Layout(left, top, width, height, drawBottomEdge, hasBelow);
        }

        @Override
        public Layout layout() {
            return layout;
        }

        @Override
        public SlotArea currentSlotArea() {
            return new SlotArea(layout.left(), layout.top(), layout.w(), layout.h());
        }

        @Override
        public BelowArea currentBelowArea() {
            return new BelowArea(layout.left(), layout.top(), layout.w(), layout.h());
        }
    }

    private enum EmptyBackgroundPainter implements BgPainter {
        INSTANCE;

        @Override
        public void paint(GuiGraphics guiGraphics, FramePainter framePainter) {
        }
    }
}
