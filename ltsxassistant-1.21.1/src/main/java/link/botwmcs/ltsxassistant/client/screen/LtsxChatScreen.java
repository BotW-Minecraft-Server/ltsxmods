package link.botwmcs.ltsxassistant.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.fizzy.ui.background.BgPainter;
import link.botwmcs.fizzy.ui.core.FizzyGui;
import link.botwmcs.fizzy.ui.core.FizzyGuiBuilder;
import link.botwmcs.fizzy.ui.core.HostType;
import link.botwmcs.fizzy.ui.frame.FrameMetrics;
import link.botwmcs.fizzy.ui.frame.FramePainter;
import link.botwmcs.fizzy.ui.host.FizzyScreenHost;
import link.botwmcs.ltsxassistant.api.chat.AdvancedChatWindowService;
import link.botwmcs.ltsxassistant.client.elements.BadgeButtonElement;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * Fizzy-hosted assistant chat screen.
 */
public final class LtsxChatScreen extends FizzyScreenHost {
    public static final double MOUSE_SCROLL_SPEED = 7.0;
    private static final Component TITLE = Component.translatable("chat_screen.title");
    private static final Component USAGE_TEXT = Component.translatable("chat_screen.usage");
    private static final int TOOLTIP_MAX_WIDTH = 210;
    private static final int CHAT_WINDOW_OFFSET_PX = 12;
    private static final int BUTTON_SIDE_MARGIN_PX = 4;
    private static final int BUTTON_GAP_PX = 4;
    private static final int BUTTON_BOTTOM_Y_PX = 30;
    private static final int BUTTON_TEXT_PADDING_PX = 3;
    private static final int BUTTON_HEIGHT_PX = 14;

    private String historyBuffer = "";
    private int historyPos = -1;
    protected EditBox input;
    private String initial;
    private CommandSuggestions commandSuggestions;

    public LtsxChatScreen(String initial) {
        this(initial, currentWidth(), currentHeight(), resolveButtons());
    }

    private LtsxChatScreen(String initial, int width, int height, List<AdvancedChatWindowService.ChatButtonSpec> buttons) {
        super(buildGui(width, height, buttons));
        this.initial = Objects.requireNonNullElse(initial, "");
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
        minecraft.setScreen(new LtsxChatScreen(current, width, height, resolveButtons()));
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
        scrollY = Mth.clamp(scrollY, -1.0, 1.0);
        if (this.commandSuggestions != null && this.commandSuggestions.mouseScrolled(scrollY)) {
            return true;
        }
        if (!hasShiftDown()) {
            scrollY *= MOUSE_SCROLL_SPEED;
        }
        this.minecraft.gui.getChat().scrollChat((int) scrollY);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.commandSuggestions != null && this.commandSuggestions.mouseClicked((double) ((int) mouseX), (double) ((int) mouseY), button)) {
            return true;
        }

        if (button == 0) {
            ChatComponent chat = this.minecraft.gui.getChat();
            if (chat.handleChatQueueClicked(mouseX, mouseY + CHAT_WINDOW_OFFSET_PX)) {
                return true;
            }

            Style style = this.getComponentStyleAt(mouseX, mouseY + CHAT_WINDOW_OFFSET_PX);
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
        renderShiftedChat(guiGraphics, mouseX, mouseY);
        guiGraphics.fill(2, this.height - 14, this.width - 2, this.height - 2, this.minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
        this.input.render(guiGraphics, mouseX, mouseY, partialTick);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        if (this.commandSuggestions != null) {
            this.commandSuggestions.render(guiGraphics, mouseX, mouseY);
        }
        guiGraphics.pose().popPose();

        GuiMessageTag messageTag = this.minecraft.gui.getChat().getMessageTagAt((double) mouseX, (double) mouseY + CHAT_WINDOW_OFFSET_PX);
        if (messageTag != null && messageTag.text() != null) {
            guiGraphics.renderTooltip(this.font, this.font.split(messageTag.text(), TOOLTIP_MAX_WIDTH), mouseX, mouseY);
            return;
        }

        Style style = this.getComponentStyleAt((double) mouseX, (double) mouseY + CHAT_WINDOW_OFFSET_PX);
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

        if (message.startsWith("/")) {
            this.minecraft.player.connection.sendCommand(message.substring(1));
        } else {
            this.minecraft.player.connection.sendChat(message);
        }
    }

    public String normalizeChatMessage(String message) {
        return StringUtil.trimChatMessage(StringUtils.normalizeSpace(message.trim()));
    }

    private void renderShiftedChat(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, -CHAT_WINDOW_OFFSET_PX, 0.0F);
        this.minecraft.gui.getChat().render(guiGraphics, this.minecraft.gui.getGuiTicks(), mouseX, mouseY, true);
        guiGraphics.pose().popPose();
    }

    private static FizzyGui buildGui(int width, int height, List<AdvancedChatWindowService.ChatButtonSpec> buttons) {
        FizzyGuiBuilder builder = FizzyGuiBuilder.start()
                .sizeSlots(1, 1)
                .host(HostType.SCREEN)
                .frame(new ViewportFramePainter())
                .background(EmptyBackgroundPainter.INSTANCE)
                .overrideSizePx(width, height);
        addButtons(builder, width, height, buttons);
        return builder.build();
    }

    private static void addButtons(
            FizzyGuiBuilder builder,
            int width,
            int height,
            List<AdvancedChatWindowService.ChatButtonSpec> buttons
    ) {
        if (buttons == null || buttons.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int cursorX = BUTTON_SIDE_MARGIN_PX;
        int rowY = Math.max(0, height - BUTTON_BOTTOM_Y_PX);
        int maxRight = Math.max(BUTTON_SIDE_MARGIN_PX, width - BUTTON_SIDE_MARGIN_PX);

        for (AdvancedChatWindowService.ChatButtonSpec spec : buttons) {
            if (spec == null) {
                continue;
            }
            int tagWidth = Math.max(24, minecraft.font.width(spec.label()) + BUTTON_TEXT_PADDING_PX * 2);
            int padWidth = tagWidth + 1;
            if (cursorX + padWidth > maxRight) {
                break;
            }
            builder.padByPx(cursorX, rowY, padWidth, BUTTON_HEIGHT_PX)
                    .element(BadgeButtonElement.builder(() -> dispatchButtonClick(spec.button()))
                            .text(spec.label())
                            .tagWidthPx(tagWidth)
                            .shadow(true)
                            .build())
                    .done();
            cursorX += padWidth + BUTTON_GAP_PX;
        }
    }

    private static void dispatchButtonClick(AdvancedChatWindowService.ChatButton button) {
        if (button == null) {
            return;
        }
        CoreServices.getOptional(AdvancedChatWindowService.class)
                .ifPresent(service -> service.onButtonPressed(button));
    }

    private static List<AdvancedChatWindowService.ChatButtonSpec> resolveButtons() {
        LocalPlayer player = Minecraft.getInstance().player;
        UUIDLike identity = player == null
                ? new UUIDLike(null, 0)
                : new UUIDLike(player.getUUID(), player.getPermissionLevel());
        return CoreServices.getOptional(AdvancedChatWindowService.class)
                .map(service -> service.resolveButtons(identity.uuid(), identity.permissionLevel()))
                .orElseGet(() -> {
                    List<AdvancedChatWindowService.ChatButtonSpec> fallback = new ArrayList<>(3);
                    fallback.add(new AdvancedChatWindowService.ChatButtonSpec(
                            AdvancedChatWindowService.ChatButton.CHAT,
                            Component.literal("Chat")
                    ));
                    fallback.add(new AdvancedChatWindowService.ChatButtonSpec(
                            AdvancedChatWindowService.ChatButton.GROUP,
                            Component.literal("Group")
                    ));
                    fallback.add(new AdvancedChatWindowService.ChatButtonSpec(
                            AdvancedChatWindowService.ChatButton.AGENT,
                            Component.literal("Agent")
                    ));
                    return List.copyOf(fallback);
                });
    }

    private static int currentWidth() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 320 : minecraft.getWindow().getGuiScaledWidth();
    }

    private static int currentHeight() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 240 : minecraft.getWindow().getGuiScaledHeight();
    }

    private record UUIDLike(@Nullable java.util.UUID uuid, int permissionLevel) {
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
