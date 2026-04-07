package link.botwmcs.ltsxassistant.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import link.botwmcs.fizzy.client.elements.VanillaLikeAbstractButton;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.ui.background.BgPainter;
import link.botwmcs.fizzy.ui.behind.VanillaBehind;
import link.botwmcs.fizzy.ui.core.FizzyGui;
import link.botwmcs.fizzy.ui.core.FizzyGuiBuilder;
import link.botwmcs.fizzy.ui.core.HostType;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.button.VanillaLikeButtonElement;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.element.funstuff.vector.SimpleDraggableElement;
import link.botwmcs.fizzy.ui.element.icon.FizzyIcon;
import link.botwmcs.fizzy.ui.frame.FrameMetrics;
import link.botwmcs.fizzy.ui.frame.FramePainter;
import link.botwmcs.fizzy.ui.host.FizzyScreenHost;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.client.elements.AutoWrapCellListElement;
import link.botwmcs.ltsxassistant.client.elements.BadgeComponentElement;
import link.botwmcs.ltsxassistant.client.elements.DarkPanelElement;
import link.botwmcs.ltsxassistant.client.elements.HoverOverlayPadElement;
import link.botwmcs.ltsxassistant.client.elements.PlayerEntityElements;
import link.botwmcs.ltsxassistant.client.elements.SkinSourceTabElement;
import link.botwmcs.ltsxassistant.client.utils.PlayerHeadRenderUtils;
import link.botwmcs.ltsxassistant.client.utils.PlayerSkinPaletteUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Responsive skin workbench screen.
 * Uses VanillaBehind only, with no visual background painter.
 */
public final class SkinWorkbenchScreen extends FizzyScreenHost {
    private static final Component TITLE = Component.translatable("screen.ltsxassistant.skin_workbench.title");
    private final SourceTab sourceTab;

    public SkinWorkbenchScreen() {
        this(SourceTab.LITTLE_SKIN, currentWidth(), currentHeight());
    }

    public SkinWorkbenchScreen(SourceTab sourceTab) {
        this(sourceTab, currentWidth(), currentHeight());
    }

    private SkinWorkbenchScreen(SourceTab sourceTab, int width, int height) {
        super(buildGui(Objects.requireNonNull(sourceTab, "sourceTab"), width, height));
        this.sourceTab = sourceTab;
    }

    public static void open(SourceTab tab) {
        Minecraft.getInstance().setScreen(new SkinWorkbenchScreen(tab));
    }

    public static void open() {
        open(SourceTab.LITTLE_SKIN);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        minecraft.setScreen(new SkinWorkbenchScreen(sourceTab, width, height));
    }

    @Override
    public Component getNarrationMessage() {
        return TITLE;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static FizzyGui buildGui(SourceTab sourceTab, int width, int height) {
        FizzyGuiBuilder builder = FizzyGuiBuilder.start()
                .sizeSlots(1, 1)
                .host(HostType.SCREEN)
                .frame(new ViewportFramePainter())
                // Keep background visually empty so only VanillaBehind is visible.
                .background(EmptyBackgroundPainter.INSTANCE)
                .behind(new VanillaBehind())
                .overrideSizePx(width, height);

        int margin = clamp(
                Math.round(Math.min(width, height) * LayoutConfig.ROOT_MARGIN_RATIO),
                LayoutConfig.ROOT_MARGIN_MIN_PX,
                LayoutConfig.ROOT_MARGIN_MAX_PX
        );
        int sectionGap = clamp(
                Math.round(width * LayoutConfig.SECTION_GAP_RATIO),
                LayoutConfig.SECTION_GAP_MIN_PX,
                LayoutConfig.SECTION_GAP_MAX_PX
        );
        int contentX = margin;
        int contentY = margin + LayoutConfig.TITLE_HEIGHT_PX + LayoutConfig.TITLE_CONTENT_GAP_PX;
        int contentWidth = Math.max(1, width - margin * 2);
        int contentHeight = Math.max(1, height - contentY - margin);

        int leftWidth = Math.max(LayoutConfig.LEFT_PANEL_MIN_WIDTH_PX, (contentWidth - sectionGap) / 4);
        int rightWidth = Math.max(1, contentWidth - leftWidth - sectionGap);
        int leftX = contentX;
        int rightX = leftX + leftWidth + sectionGap;

        addTitle(builder, contentX, margin, contentWidth);
        addLeftPreviewSection(builder, leftX, contentY, leftWidth, contentHeight);
        addRightSkinSection(builder, sourceTab, rightX, contentY, rightWidth, contentHeight);
        return builder.build();
    }

    private static void addTitle(FizzyGuiBuilder builder, int x, int y, int width) {
        builder.padByPx(x, y, Math.max(1, width), LayoutConfig.TITLE_HEIGHT_PX)
                .element(new FizzyComponentElement.Builder()
                        .addText(TITLE)
                        .align(TextRenderer.Align.LEFT)
                        .shadow(true)
                        .wrap(false)
                        .autoEllipsis(true)
                        .build())
                .done();
    }

    private static void addLeftPreviewSection(FizzyGuiBuilder builder, int x, int y, int width, int height) {
        builder.padByPx(x, y, width, height)
                .element(new DarkPanelElement(0xE1131418, 0xE11B1C22))
                .done();

        int panelPadding = clamp(
                Math.round(Math.min(width, height) * LayoutConfig.LEFT_PANEL_PADDING_RATIO),
                LayoutConfig.PANEL_PADDING_MIN_PX,
                LayoutConfig.PANEL_PADDING_MAX_PX
        );
        int innerX = x + panelPadding;
        int innerY = y + panelPadding;
        int innerWidth = Math.max(1, width - panelPadding * 2);
        int innerHeight = Math.max(1, height - panelPadding * 2);

        int buttonRowWidth = Math.min(LayoutConfig.LEFT_BUTTONS_WIDTH_PX, innerWidth);
        int buttonY = y + height - panelPadding - LayoutConfig.LEFT_ICON_BUTTON_SIZE_PX;
        int buttonX = x + (width - buttonRowWidth) / 2;
        int previewHeight = Math.max(40, buttonY - innerY - panelPadding);

        builder.padByPx(innerX, innerY, innerWidth, previewHeight)
                .element(new PlayerEntityElements())
                .done();
        addLeftPreviewBadge(builder, innerX, innerY, innerWidth);

        builder.padByPx(buttonX, buttonY, LayoutConfig.LEFT_ICON_BUTTON_SIZE_PX, LayoutConfig.LEFT_ICON_BUTTON_SIZE_PX)
                .element(buildLeftSettingsButton())
                .done();
        builder.padByPx(
                        buttonX + LayoutConfig.LEFT_ICON_BUTTON_SIZE_PX + LayoutConfig.LEFT_BUTTON_GAP_PX,
                        buttonY,
                        Math.max(LayoutConfig.CELL_PLAYER_MIN_SIZE_PX, buttonRowWidth - LayoutConfig.LEFT_ICON_BUTTON_SIZE_PX - LayoutConfig.LEFT_BUTTON_GAP_PX),
                        LayoutConfig.LEFT_ICON_BUTTON_SIZE_PX
                )
                .element(buildLeftLittleSkinButton())
                .done();
    }

    private static void addLeftPreviewBadge(FizzyGuiBuilder builder, int previewX, int previewY, int previewWidth) {
        if (previewWidth <= 2) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String username = PlayerHeadRenderUtils.currentUsername();
        PlayerSkinPaletteUtils.SkinPalette palette = PlayerSkinPaletteUtils.currentPlayerPalette();

        int maxTagWidth = Math.max(1, previewWidth - (LayoutConfig.LEFT_BADGE_SIDE_MARGIN_PX * 2) - 1);
        int minTagWidth = Math.min(LayoutConfig.LEFT_BADGE_MIN_WIDTH_PX, maxTagWidth);
        int desiredTagWidth = clamp(
                minecraft.font.width(username) + (LayoutConfig.LEFT_BADGE_TEXT_HORIZONTAL_PADDING_PX * 2),
                minTagWidth,
                maxTagWidth
        );

        int badgeWidth = desiredTagWidth + 1;
        int badgeHeight = minecraft.font.lineHeight + LayoutConfig.LEFT_BADGE_HEIGHT_EXTRA_PX;
        int badgeX = previewX + Math.max(0, (previewWidth - badgeWidth) / 2);
        int badgeY = previewY + LayoutConfig.LEFT_BADGE_TOP_MARGIN_PX;

        builder.padByPx(badgeX, badgeY, badgeWidth, badgeHeight)
                .element(BadgeComponentElement.builder()
                        .text(Component.literal(username))
                        .tagWidthPx(desiredTagWidth)
                        .outlineColor(palette.outlineColor())
                        .fillColor(palette.fillColor())
                        .textColor(palette.textColor())
                        .shadow(true)
                        .build())
                .done();
    }

    private static void addRightSkinSection(
            FizzyGuiBuilder builder,
            SourceTab sourceTab,
            int x,
            int y,
            int width,
            int height
    ) {
        int panelWidth = Math.max(1, width);
        int panelHeight = Math.max(1, height);
        int tabY = Math.max(0, y - LayoutConfig.TAB_HEIGHT_PX - LayoutConfig.TAB_ABOVE_PANEL_GAP_PX);
        builder.padByPx(x, tabY, panelWidth, LayoutConfig.TAB_HEIGHT_PX)
                .element(buildTabElement(sourceTab))
                .done();

        builder.padByPx(x, y, panelWidth, panelHeight)
                .element(new DarkPanelElement(0xBF0C0D12, 0xBF141622))
                .done();

        int panelPadding = clamp(
                Math.round(Math.min(panelWidth, panelHeight) * LayoutConfig.RIGHT_PANEL_PADDING_RATIO),
                LayoutConfig.PANEL_PADDING_MIN_PX,
                LayoutConfig.PANEL_PADDING_MAX_PX
        );
        int innerX = x + panelPadding;
        int innerY = y + panelPadding;
        int innerWidth = Math.max(1, panelWidth - panelPadding * 2);
        int innerHeight = Math.max(1, panelHeight - panelPadding * 2);

        addRightTabPage(builder, sourceTab, innerX, innerY, innerWidth, innerHeight);
    }

    private static ElementPainter buildTabElement(SourceTab sourceTab) {
        return new SkinSourceTabElement(
                List.of(
                        new SkinSourceTabElement.TabSpec(
                                SourceTab.LITTLE_SKIN.id(),
                                Component.translatable("screen.ltsxassistant.skin_workbench.tab.littleskin")
                        ),
                        new SkinSourceTabElement.TabSpec(
                                SourceTab.LOCAL_SKIN.id(),
                                Component.translatable("screen.ltsxassistant.skin_workbench.tab.localskin")
                        )
                ),
                sourceTab.id(),
                selected -> {
                    SourceTab next = SourceTab.fromId(selected);
                    if (next != sourceTab) {
                        open(next);
                    }
                }
        );
    }

    private static void addRightTabPage(
            FizzyGuiBuilder builder,
            SourceTab sourceTab,
            int x,
            int y,
            int width,
            int height
    ) {
        if (sourceTab == SourceTab.LITTLE_SKIN) {
            if (!isLittleSkinConnectedPlaceholder()) {
                addCenteredStatusPage(
                        builder,
                        x,
                        y,
                        width,
                        height,
                        Component.translatable("screen.ltsxassistant.skin_workbench.page.littleskin.disconnected"),
                        buildLittleSkinConnectPageButton()
                );
                return;
            }
            addScrollableListPage(builder, SourceTab.LITTLE_SKIN, x, y, width, height);
            return;
        }

        if (!hasLocalSkinsPlaceholder()) {
            addCenteredStatusPage(
                    builder,
                    x,
                    y,
                    width,
                    height,
                    Component.translatable("screen.ltsxassistant.skin_workbench.page.localskin.empty"),
                    buildLocalSkinUploadPageButton()
            );
            return;
        }
        addScrollableListPage(builder, SourceTab.LOCAL_SKIN, x, y, width, height);
    }

    private static void addScrollableListPage(
            FizzyGuiBuilder builder,
            SourceTab sourceTab,
            int x,
            int y,
            int width,
            int height
    ) {
        builder.padByPx(x, y, width, height)
                .element(buildSkinScroller(sourceTab, width, height))
                .done();
    }

    private static void addCenteredStatusPage(
            FizzyGuiBuilder builder,
            int x,
            int y,
            int width,
            int height,
            Component message,
            VanillaLikeButtonElement actionButton
    ) {
        int textWidth = Math.max(1, width - LayoutConfig.PAGE_SIDE_PADDING_PX * 2);
        int textX = x + Math.max(0, (width - textWidth) / 2);
        int textHeight = Math.min(
                LayoutConfig.PAGE_TEXT_MAX_HEIGHT_PX,
                Math.max(24, Minecraft.getInstance().font.lineHeight * 4)
        );
        int buttonWidth = clamp(
                width - LayoutConfig.PAGE_SIDE_PADDING_PX * 2,
                LayoutConfig.PAGE_BUTTON_MIN_WIDTH_PX,
                LayoutConfig.PAGE_BUTTON_MAX_WIDTH_PX
        );
        int blockHeight = textHeight + LayoutConfig.PAGE_TEXT_BUTTON_GAP_PX + LayoutConfig.PAGE_BUTTON_HEIGHT_PX;
        int startY = y + Math.max(0, (height - blockHeight) / 2);
        int buttonX = x + Math.max(0, (width - buttonWidth) / 2);

        builder.padByPx(textX, startY, textWidth, textHeight)
                .element(new FizzyComponentElement.Builder()
                        .addText(message)
                        .align(TextRenderer.Align.CENTER)
                        .shadow(true)
                        .wrap(true)
                        .autoEllipsis(true)
                        .clipToPad(true)
                        .allowOverflow(false)
                        .build())
                .done();

        builder.padByPx(
                        buttonX,
                        startY + textHeight + LayoutConfig.PAGE_TEXT_BUTTON_GAP_PX,
                        buttonWidth,
                        LayoutConfig.PAGE_BUTTON_HEIGHT_PX
                )
                .element(actionButton)
                .done();
    }

    private static boolean isLittleSkinConnectedPlaceholder() {
        // TODO replace with actual LittleSkin OAuth state.
        return false;
    }

    private static boolean hasLocalSkinsPlaceholder() {
        // TODO replace with local skin repository check.
        return false;
    }

    private static ElementPainter buildSkinScroller(SourceTab sourceTab, int viewportWidth, int viewportHeight) {
        List<SkinCell> cells = buildCells(sourceTab);
        int contentWidth = Math.max(1, viewportWidth - LayoutConfig.SCROLLBAR_WIDTH_PX - LayoutConfig.SCROLLBAR_GAP_PX);
        int columns = resolveColumns(contentWidth);
        int cellWidth = resolveCellWidth(contentWidth, columns);
        int cellHeight = clamp(
                cellWidth + LayoutConfig.CELL_HEIGHT_EXTRA_PX,
                LayoutConfig.CELL_MIN_HEIGHT_PX,
                LayoutConfig.CELL_MAX_HEIGHT_PX
        );

        AutoWrapCellListElement.Builder listBuilder = AutoWrapCellListElement.builder()
                .gapPx(LayoutConfig.GRID_MIN_GAP_PX, LayoutConfig.GRID_ROW_GAP_PX)
                .rowAlign(AutoWrapCellListElement.RowAlign.JUSTIFY)
                .paddingPx(0, 0, 0, 0);
        for (int index = 0; index < cells.size(); index++) {
            SkinCell cell = cells.get(index);
            int cellIndex = index;
            listBuilder.cell(cellBuilder -> {
                cellBuilder.sizePx(cellWidth, cellHeight);
                if (cell.plusSlot()) {
                    cellBuilder.element(buildPlusCellElement());
                    cellBuilder.element(buildCellHoverActionElement(cell, cellIndex, cellWidth, cellHeight));
                    return;
                }

                int buttonReserveRight = LayoutConfig.CELL_BUTTON_SIZE_PX
                        + LayoutConfig.CELL_BUTTON_MARGIN_PX
                        + LayoutConfig.CELL_BUTTON_SAFE_GAP_PX;
                int playerWidth = Math.max(
                        LayoutConfig.CELL_PLAYER_MIN_SIZE_PX,
                        cellWidth - LayoutConfig.CELL_PLAYER_LEFT_PAD_PX - buttonReserveRight
                );
                int playerHeight = Math.max(
                        LayoutConfig.CELL_PLAYER_MIN_SIZE_PX,
                        cellHeight - LayoutConfig.CELL_PLAYER_TOP_PAD_PX - LayoutConfig.CELL_PLAYER_BOTTOM_PAD_PX
                );
                cellBuilder.padByPx(
                        LayoutConfig.CELL_PLAYER_LEFT_PAD_PX,
                        LayoutConfig.CELL_PLAYER_TOP_PAD_PX,
                        playerWidth,
                        playerHeight,
                        pad -> pad.element(new PlayerEntityElements(false))
                );
                cellBuilder.element(buildCellHoverActionElement(cell, cellIndex, cellWidth, cellHeight));
            });
        }
        AutoWrapCellListElement listElement = listBuilder.build();
        int contentHeight = Math.max(viewportHeight, listElement.measureContentHeight(contentWidth));

        SimpleDraggableElement.ContentBuilder contentBuilder = SimpleDraggableElement.contentBuilder();
        contentBuilder.contentHeightPx(contentHeight);
        contentBuilder.padByPx(0, 0, contentWidth, contentHeight)
                .element(listElement)
                .done();

        return SimpleDraggableElement.builder(contentBuilder.build())
                .wheelStepPx(LayoutConfig.SCROLL_WHEEL_STEP_PX)
                .scrollbarWidthPx(LayoutConfig.SCROLLBAR_WIDTH_PX)
                .scrollbarGapPx(LayoutConfig.SCROLLBAR_GAP_PX)
                .minThumbHeightPx(LayoutConfig.SCROLLBAR_MIN_THUMB_HEIGHT_PX)
                .build();
    }

    private static int resolveColumns(int contentWidth) {
        int columns = Math.max(1, contentWidth / (LayoutConfig.GRID_MIN_CELL_WIDTH_PX + LayoutConfig.GRID_MIN_GAP_PX));
        columns = Math.min(columns, LayoutConfig.GRID_MAX_COLUMNS);
        int cellWidth = resolveCellWidth(contentWidth, columns);
        while (columns < LayoutConfig.GRID_MAX_COLUMNS && cellWidth > LayoutConfig.GRID_MAX_CELL_WIDTH_PX) {
            int nextColumns = columns + 1;
            int nextWidth = resolveCellWidth(contentWidth, nextColumns);
            if (nextWidth < LayoutConfig.GRID_MIN_CELL_WIDTH_PX) {
                break;
            }
            columns = nextColumns;
            cellWidth = nextWidth;
        }
        return Math.max(1, columns);
    }

    private static int resolveCellWidth(int contentWidth, int columns) {
        if (columns <= 1) {
            return Math.max(1, contentWidth);
        }
        int widthForCells = Math.max(1, contentWidth - LayoutConfig.GRID_MIN_GAP_PX * (columns - 1));
        return Math.max(1, widthForCells / columns);
    }

    private static ElementPainter buildPlusCellElement() {
        return new FizzyComponentElement.Builder()
                .addText(Component.translatable("screen.ltsxassistant.skin_workbench.cell.plus"))
                .align(TextRenderer.Align.CENTER)
                .shadow(true)
                .clipToPad(true)
                .allowOverflow(false)
                .build();
    }

    private static ElementPainter buildCellHoverActionElement(SkinCell cell, int index, int cellWidth, int cellHeight) {
        HoverOverlayPadElement.Builder builder = HoverOverlayPadElement.builder()
                .hoverOverlayColor(0x54FFFFFF);
        if (cell.plusSlot()) {
            return builder.build();
        }
        int buttonX = Math.max(0, cellWidth - LayoutConfig.CELL_BUTTON_SIZE_PX - LayoutConfig.CELL_BUTTON_MARGIN_PX);
        int buttonY = LayoutConfig.CELL_BUTTON_MARGIN_PX;
        builder.padByPx(
                buttonX,
                buttonY,
                LayoutConfig.CELL_BUTTON_SIZE_PX,
                LayoutConfig.CELL_BUTTON_SIZE_PX,
                pad -> pad.element(buildSetSkinButton(cell, index))
        );
        return builder.build();
    }

    private static ElementPainter buildSetSkinButton(SkinCell cell, int index) {
        return VanillaLikeButtonElement.builder(button ->
                        LTSXAssistant.LOGGER.info("[ltsxassistant] TODO set hovered skin index={} source={}", index, cell.source())
                )
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.BLUE)
                .text(new FizzyComponentElement.Builder()
                        .addText(Component.translatable("screen.ltsxassistant.skin_workbench.cell.set.short"))
                        .align(TextRenderer.Align.CENTER)
                        .shadow(true)
                        .wrap(false)
                        .autoEllipsis(false)
                        .build())
                .contentPaddingPx(0, 0)
                .tooltip(Component.translatable("screen.ltsxassistant.skin_workbench.cell.set.tooltip"))
                .narration(Component.translatable("screen.ltsxassistant.skin_workbench.cell.set.narration"))
                .build();
    }

    private static List<SkinCell> buildCells(SourceTab sourceTab) {
        List<SkinCell> cells = new ArrayList<>();
        int count = sourceTab == SourceTab.LITTLE_SKIN
                ? LayoutConfig.LITTLE_SKIN_LIST_PLACEHOLDER_COUNT
                : LayoutConfig.LOCAL_SKIN_LIST_PLACEHOLDER_COUNT;
        for (int i = 0; i < count; i++) {
            cells.add(new SkinCell(sourceTab.id(), false));
        }
        if (sourceTab == SourceTab.LOCAL_SKIN) {
            cells.add(new SkinCell(sourceTab.id(), true));
        }
        return cells;
    }

    private static VanillaLikeButtonElement buildLeftSettingsButton() {
        return VanillaLikeButtonElement.builder(button -> LTSXAssistant.LOGGER.info("[ltsxassistant] TODO open skin settings."))
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                .icon(FizzyIcon.SETTINGS_9X8.texture(), false, false)
                .iconSizePx(8)
                .contentPaddingPx(0, 0)
                .tooltip(Component.translatable("screen.ltsxassistant.skin_workbench.left.settings.tooltip"))
                .narration(Component.translatable("screen.ltsxassistant.skin_workbench.left.settings.narration"))
                .build();
    }

    private static VanillaLikeButtonElement buildLeftLittleSkinButton() {
        return VanillaLikeButtonElement.builder(button -> LTSXAssistant.LOGGER.info("[ltsxassistant] TODO connect LittleSkin account."))
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.BLUE)
                .layout(VanillaLikeButtonElement.ContentLayout.TEXT_LEFT_ICON_RIGHT)
                .text(new FizzyComponentElement.Builder()
                        .addText(Component.translatable("screen.ltsxassistant.skin_workbench.left.littleskin"))
                        .shadow(true)
                        .wrap(false)
                        .autoEllipsis(true)
                        .build())
                .icon(FizzyIcon.WORLD_8X8.texture(), false, false)
                .iconSizePx(8)
                .contentGapPx(4)
                .contentPaddingPx(6, 6)
                .tooltip(Component.translatable("screen.ltsxassistant.skin_workbench.left.littleskin.tooltip"))
                .narration(Component.translatable("screen.ltsxassistant.skin_workbench.left.littleskin.narration"))
                .build();
    }

    private static VanillaLikeButtonElement buildLittleSkinConnectPageButton() {
        return VanillaLikeButtonElement.builder(
                        button -> LTSXAssistant.LOGGER.info("[ltsxassistant] TODO open LittleSkin OAuth connect page in browser.")
                )
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.BLUE)
                .text(new FizzyComponentElement.Builder()
                        .addText(Component.translatable("screen.ltsxassistant.skin_workbench.page.littleskin.connect.button"))
                        .align(TextRenderer.Align.CENTER)
                        .shadow(true)
                        .wrap(false)
                        .autoEllipsis(true)
                        .build())
                .tooltip(Component.translatable("screen.ltsxassistant.skin_workbench.page.littleskin.connect.tooltip"))
                .narration(Component.translatable("screen.ltsxassistant.skin_workbench.page.littleskin.connect.narration"))
                .build();
    }

    private static VanillaLikeButtonElement buildLocalSkinUploadPageButton() {
        return VanillaLikeButtonElement.builder(
                        button -> LTSXAssistant.LOGGER.info("[ltsxassistant] TODO upload local skin action.")
                )
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.BLUE)
                .text(new FizzyComponentElement.Builder()
                        .addText(Component.translatable("screen.ltsxassistant.skin_workbench.page.localskin.upload.button"))
                        .align(TextRenderer.Align.CENTER)
                        .shadow(true)
                        .wrap(false)
                        .autoEllipsis(true)
                        .build())
                .tooltip(Component.translatable("screen.ltsxassistant.skin_workbench.page.localskin.upload.tooltip"))
                .narration(Component.translatable("screen.ltsxassistant.skin_workbench.page.localskin.upload.narration"))
                .build();
    }

    private static int currentWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int currentHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Centralized tweak area for responsive layout tuning.
     * Adjust values here to quickly iterate UI density and spacing.
     */
    private static final class LayoutConfig {
        private static final float ROOT_MARGIN_RATIO = 0.018f;
        private static final float SECTION_GAP_RATIO = 0.012f;
        private static final float LEFT_PANEL_PADDING_RATIO = 0.03f;
        private static final float RIGHT_PANEL_PADDING_RATIO = 0.02f;
        private static final int ROOT_MARGIN_MIN_PX = 8;
        private static final int ROOT_MARGIN_MAX_PX = 20;
        private static final int SECTION_GAP_MIN_PX = 8;
        private static final int SECTION_GAP_MAX_PX = 18;
        private static final int LEFT_PANEL_MIN_WIDTH_PX = 140;
        private static final int TITLE_HEIGHT_PX = 14;
        private static final int TITLE_CONTENT_GAP_PX = 4;
        private static final int TAB_HEIGHT_PX = 24;
        private static final int TAB_ABOVE_PANEL_GAP_PX = -2;
        private static final int TAB_TO_LIST_GAP_PX = 2;
        private static final int PANEL_PADDING_MIN_PX = 8;
        private static final int PANEL_PADDING_MAX_PX = 16;
        private static final int PAGE_SIDE_PADDING_PX = 12;
        private static final int PAGE_TEXT_MAX_HEIGHT_PX = 56;
        private static final int PAGE_TEXT_BUTTON_GAP_PX = 10;
        private static final int PAGE_BUTTON_HEIGHT_PX = 20;
        private static final int PAGE_BUTTON_MIN_WIDTH_PX = 130;
        private static final int PAGE_BUTTON_MAX_WIDTH_PX = 196;
        private static final int LEFT_BUTTONS_WIDTH_PX = 80;
        private static final int LEFT_BUTTON_GAP_PX = 4;
        private static final int LEFT_ICON_BUTTON_SIZE_PX = 20;
        private static final int LEFT_BADGE_SIDE_MARGIN_PX = 6;
        private static final int LEFT_BADGE_TOP_MARGIN_PX = 2;
        private static final int LEFT_BADGE_MIN_WIDTH_PX = 40;
        private static final int LEFT_BADGE_TEXT_HORIZONTAL_PADDING_PX = 3;
        private static final int LEFT_BADGE_HEIGHT_EXTRA_PX = 3;
        private static final int SCROLLBAR_WIDTH_PX = 6;
        private static final int SCROLLBAR_GAP_PX = 2;
        private static final int SCROLLBAR_MIN_THUMB_HEIGHT_PX = 18;
        private static final int SCROLL_WHEEL_STEP_PX = 18;
        private static final int LITTLE_SKIN_LIST_PLACEHOLDER_COUNT = 10;
        private static final int LOCAL_SKIN_LIST_PLACEHOLDER_COUNT = 5;
        private static final int GRID_MIN_GAP_PX = 12;
        private static final int GRID_ROW_GAP_PX = 8;
        private static final int GRID_MAX_COLUMNS = 8;
        private static final int GRID_MIN_CELL_WIDTH_PX = 64;
        private static final int GRID_MAX_CELL_WIDTH_PX = 140;
        private static final int CELL_HEIGHT_EXTRA_PX = 28;
        private static final int CELL_MIN_HEIGHT_PX = 88;
        private static final int CELL_MAX_HEIGHT_PX = 192;
        private static final int CELL_PLAYER_MIN_SIZE_PX = 64;
        private static final int CELL_PLAYER_LEFT_PAD_PX = 4;
        private static final int CELL_PLAYER_TOP_PAD_PX = 12;
        private static final int CELL_PLAYER_BOTTOM_PAD_PX = 4;
        private static final int CELL_BUTTON_SIZE_PX = 20;
        private static final int CELL_BUTTON_MARGIN_PX = 2;
        private static final int CELL_BUTTON_SAFE_GAP_PX = 5;

        private LayoutConfig() {
        }
    }

    private record SkinCell(String source, boolean plusSlot) {
    }

    public enum SourceTab {
        LITTLE_SKIN("littleskin"),
        LOCAL_SKIN("localskin");

        private final String id;

        SourceTab(String id) {
            this.id = id;
        }

        public static SourceTab fromId(String id) {
            for (SourceTab tab : values()) {
                if (tab.id.equals(id)) {
                    return tab;
                }
            }
            return LITTLE_SKIN;
        }

        public String id() {
            return id;
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
        public void paint(
                GuiGraphics guiGraphics,
                int left,
                int top,
                int width,
                int height,
                boolean drawBottomEdge,
                boolean hasBelow
        ) {
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
