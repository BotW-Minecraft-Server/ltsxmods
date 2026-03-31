package link.botwmcs.core.client.debug;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.net.stat.CoreNebPacketFlowStat;
import link.botwmcs.core.net.stat.CoreNebTrafficStat;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.ui.background.BgPainter;
import link.botwmcs.fizzy.ui.behind.VanillaBehind;
import link.botwmcs.fizzy.ui.core.FizzyGui;
import link.botwmcs.fizzy.ui.core.FizzyGuiBuilder;
import link.botwmcs.fizzy.ui.core.HostType;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.element.component.SimpleChartsElement;
import link.botwmcs.fizzy.ui.element.funstuff.vector.SimpleDraggableElement;
import link.botwmcs.fizzy.ui.frame.FrameMetrics;
import link.botwmcs.fizzy.ui.frame.FramePainter;
import link.botwmcs.fizzy.ui.host.FizzyScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Real-time traffic view for Core NEB transport rendered by Fizzy.
 */
public final class CoreNetworkStatScreen extends FizzyScreenHost {
    private static final Component TITLE = Component.literal("LTSXCore Networking Debug");
    private static final int LINE_HEIGHT = 12;
    private static final int LEFT_PADDING = 10;
    private static final int RIGHT_PADDING = 10;
    private static final int TOP_PADDING = 12;
    private static final int TABLE_GAP = 12;
    private static final int BOTTOM_PADDING = 8;
    private static final int COLOR_E = 0xFFFFFF55;
    private static final int COLOR_7 = 0xFFAAAAAA;
    private static final int COLOR_F = 0xFFFFFFFF;
    private static final int TABLE_GRID_COLUMNS = 45;
    private static final int PATH_COL_START = 1;
    private static final int PATH_COL_END = 5;
    private static final int SEP_1_COL = 6;
    private static final int TYPE_COL_START = 7;
    private static final int TYPE_COL_END = 26;
    private static final int SEP_2_COL = 27;
    private static final int SPEED_COL_START = 28;
    private static final int SPEED_COL_END = 34;
    private static final int SEP_3_COL = 35;
    private static final int TOTAL_COL_START = 36;
    private static final int TOTAL_COL_END = 40;
    private static final int SEP_4_COL = 41;
    private static final int COUNT_COL_START = 42;
    private static final int COUNT_COL_END = 45;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_GAP = 2;
    private final CoreNetworkStatModel model;
    private final TableCapacity capacity;

    public CoreNetworkStatScreen() {
        this(new CoreNetworkStatModel(), currentWidth(), currentHeight());
    }

    private CoreNetworkStatScreen(CoreNetworkStatModel model, int width, int height) {
        this(model, width, height, TableCapacity.capture(model));
    }

    private CoreNetworkStatScreen(CoreNetworkStatModel model, int width, int height, TableCapacity capacity) {
        super(buildGui(model, width, height, capacity));
        this.model = model;
        this.capacity = capacity;
    }

    @Override
    public void tick() {
        super.tick();
        model.tick();
        if (model.needsCapacity(capacity)) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new CoreNetworkStatScreen(
                    model,
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight()
            ));
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        minecraft.setScreen(new CoreNetworkStatScreen(model, width, height));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        return TITLE;
    }

    private static FizzyGui buildGui(CoreNetworkStatModel model, int width, int height, TableCapacity capacity) {
        FizzyGuiBuilder builder = FizzyGuiBuilder.start()
                .sizeSlots(1, 1)
                .host(HostType.SCREEN)
                .frame(new ViewportFramePainter())
                .background(EmptyBackgroundPainter.INSTANCE)
                .behind(new VanillaBehind())
                .overrideSizePx(width, height);

        int fullWidth = Math.max(1, width - LEFT_PADDING - RIGHT_PADDING);
        addOverflowText(builder, LEFT_PADDING, TOP_PADDING, fullWidth, () -> "Core Networking Traffic (NEB path)", () -> COLOR_E);
        addOverflowText(builder, LEFT_PADDING, TOP_PADDING + LINE_HEIGHT, fullWidth, model::modeLine, () -> COLOR_7);
        addOverflowText(builder, LEFT_PADDING, TOP_PADDING + LINE_HEIGHT * 3, fullWidth, () -> "Actual Transmission", () -> COLOR_7);
        addOverflowText(builder, LEFT_PADDING, TOP_PADDING + LINE_HEIGHT * 4, fullWidth, model::actualLine, () -> COLOR_F);
        addOverflowText(builder, LEFT_PADDING, TOP_PADDING + LINE_HEIGHT * 6, fullWidth, () -> "Raw Payload", () -> COLOR_7);
        addOverflowText(builder, LEFT_PADDING, TOP_PADDING + LINE_HEIGHT * 7, fullWidth, model::rawLine, () -> COLOR_F);
        addOverflowText(builder, LEFT_PADDING, TOP_PADDING + LINE_HEIGHT * 9, fullWidth, model::ratioLine, () -> COLOR_F);

        int tableTop = TOP_PADDING + LINE_HEIGHT * 11;
        int colWidth = Math.max(1, (width - LEFT_PADDING * 2 - TABLE_GAP) / 2);
        int rightX = LEFT_PADDING + colWidth + TABLE_GAP;
        int tableDataTop = tableTop + LINE_HEIGHT * 2;
        int tableViewportHeight = Math.max(LINE_HEIGHT, height - tableDataTop - BOTTOM_PADDING);

        addFlowSection(builder, model, FlowSection.INBOUND, LEFT_PADDING, tableTop, colWidth, tableViewportHeight, capacity.inboundRows());
        addFlowSection(builder, model, FlowSection.OUTBOUND, rightX, tableTop, colWidth, tableViewportHeight, capacity.outboundRows());
        return builder.build();
    }

    private static void addFlowSection(
            FizzyGuiBuilder builder,
            CoreNetworkStatModel model,
            FlowSection section,
            int x,
            int y,
            int width,
            int viewportHeight,
            int rowCapacity
    ) {
        addOverflowText(builder, x, y, width, section::title, () -> COLOR_E);
        addOverflowText(builder, x, y + LINE_HEIGHT, width, () -> "Path | Type | Speed | Total | Count", () -> COLOR_7);
        builder.padByPx(x, y + LINE_HEIGHT * 2, width, viewportHeight)
                .element(buildFlowScroller(model, section, width, rowCapacity))
                .done();
    }

    private static SimpleDraggableElement buildFlowScroller(
            CoreNetworkStatModel model,
            FlowSection section,
            int width,
            int rowCapacity
    ) {
        int resolvedRows = Math.max(1, rowCapacity);
        int contentWidth = Math.max(1, width - SCROLLBAR_WIDTH - SCROLLBAR_GAP);
        int contentHeight = resolvedRows * LINE_HEIGHT;

        SimpleDraggableElement.ContentBuilder contentBuilder = SimpleDraggableElement.contentBuilder();
        contentBuilder.contentHeightPx(contentHeight);
        contentBuilder.padByPx(0, 0, contentWidth, contentHeight)
                .element(buildFlowChart(model, section, resolvedRows))
                .done();
        return SimpleDraggableElement.builder(contentBuilder.build())
                .wheelStepPx(LINE_HEIGHT)
                .scrollbarWidthPx(SCROLLBAR_WIDTH)
                .scrollbarGapPx(SCROLLBAR_GAP)
                .minThumbHeightPx(LINE_HEIGHT * 2)
                .build();
    }

    private static SimpleChartsElement buildFlowChart(CoreNetworkStatModel model, FlowSection section, int rowCapacity) {
        int resolvedRows = Math.max(1, rowCapacity);
        SimpleChartsElement.ContentBuilder contentBuilder = SimpleChartsElement.contentBuilder();
        contentBuilder.grid(resolvedRows, TABLE_GRID_COLUMNS);
        for (int rowIndex = 0; rowIndex < resolvedRows; rowIndex++) {
            addFlowRow(contentBuilder, model, section, rowIndex);
        }
        return SimpleChartsElement.builder(contentBuilder.build()).build();
    }

    private static void addFlowRow(
            SimpleChartsElement.ContentBuilder contentBuilder,
            CoreNetworkStatModel model,
            FlowSection section,
            int rowIndex
    ) {
        addTableCell(contentBuilder, rowIndex, PATH_COL_START, PATH_COL_END, model::rowColor, model::pathText, section, true);
        addTableCell(contentBuilder, rowIndex, SEP_1_COL, SEP_1_COL, model::rowColor, model::separatorText, section, false);
        addTableCell(contentBuilder, rowIndex, TYPE_COL_START, TYPE_COL_END, model::rowColor, model::typeText, section, true);
        addTableCell(contentBuilder, rowIndex, SEP_2_COL, SEP_2_COL, model::rowColor, model::separatorText, section, false);
        addTableCell(contentBuilder, rowIndex, SPEED_COL_START, SPEED_COL_END, model::rowColor, model::speedText, section, true);
        addTableCell(contentBuilder, rowIndex, SEP_3_COL, SEP_3_COL, model::rowColor, model::separatorText, section, false);
        addTableCell(contentBuilder, rowIndex, TOTAL_COL_START, TOTAL_COL_END, model::rowColor, model::totalText, section, true);
        addTableCell(contentBuilder, rowIndex, SEP_4_COL, SEP_4_COL, model::rowColor, model::separatorText, section, false);
        addTableCell(contentBuilder, rowIndex, COUNT_COL_START, COUNT_COL_END, model::rowColor, model::countText, section, true);
    }

    private static void addTableCell(
            SimpleChartsElement.ContentBuilder contentBuilder,
            int rowIndex,
            int colStart,
            int colEnd,
            RowColorSupplier colorSupplier,
            RowTextSupplier textSupplier,
            FlowSection section,
            boolean autoEllipsis
    ) {
        int chartRow = rowIndex + 1;
        contentBuilder.cell(chartRow, colStart, chartRow, colEnd)
                .element(new LiveTextElement(
                        () -> textSupplier.get(section, rowIndex),
                        () -> colorSupplier.get(section, rowIndex),
                        autoEllipsis,
                        false
                ))
                .done();
    }

    private static void addOverflowText(
            FizzyGuiBuilder builder,
            int x,
            int y,
            int width,
            Supplier<String> textSupplier,
            IntSupplier colorSupplier
    ) {
        builder.padByPx(x, y, Math.max(1, width), LINE_HEIGHT)
                .element(new LiveTextElement(textSupplier, colorSupplier, false, true))
                .done();
    }

    private static int currentWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int currentHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static final class CoreNetworkStatModel {
        private String actualLine = "";
        private String rawLine = "";
        private String ratioLine = "";
        private String modeLine = "";
        private List<CoreNebPacketFlowStat.Row> inboundRows = List.of();
        private List<CoreNebPacketFlowStat.Row> outboundRows = List.of();
        private int tick;

        private CoreNetworkStatModel() {
            refresh();
        }

        private void tick() {
            if (tick % 10 == 0) {
                refresh();
            }
            tick++;
        }

        private boolean needsCapacity(TableCapacity capacity) {
            return inboundRows.size() > capacity.inboundRows() || outboundRows.size() > capacity.outboundRows();
        }

        private String actualLine() {
            return actualLine;
        }

        private String rawLine() {
            return rawLine;
        }

        private String ratioLine() {
            return ratioLine;
        }

        private String modeLine() {
            return modeLine;
        }

        private String pathText(FlowSection section, int rowIndex) {
            CoreNebPacketFlowStat.Row row = row(section, rowIndex);
            if (row != null) {
                return row.path() == CoreNebPacketFlowStat.TrafficPath.NEB ? "NEB" : "BYPASS";
            }
            return rows(section).isEmpty() && rowIndex == 0 ? "<empty>" : "";
        }

        private String separatorText(FlowSection section, int rowIndex) {
            return row(section, rowIndex) == null ? "" : "|";
        }

        private String typeText(FlowSection section, int rowIndex) {
            CoreNebPacketFlowStat.Row row = row(section, rowIndex);
            return row == null ? "" : row.type().toString();
        }

        private String speedText(FlowSection section, int rowIndex) {
            CoreNebPacketFlowStat.Row row = row(section, rowIndex);
            return row == null ? "" : readableSpeed(row.speedBytesPerSecond());
        }

        private String totalText(FlowSection section, int rowIndex) {
            CoreNebPacketFlowStat.Row row = row(section, rowIndex);
            return row == null ? "" : readableSize(row.totalBytes());
        }

        private String countText(FlowSection section, int rowIndex) {
            CoreNebPacketFlowStat.Row row = row(section, rowIndex);
            return row == null ? "" : Long.toString(row.totalPackets());
        }

        private int rowColor(FlowSection section, int rowIndex) {
            CoreNebPacketFlowStat.Row row = row(section, rowIndex);
            if (row == null) {
                return rows(section).isEmpty() && rowIndex == 0 ? COLOR_7 : COLOR_F;
            }
            return row.path() == CoreNebPacketFlowStat.TrafficPath.NEB ? COLOR_E : COLOR_F;
        }

        private List<CoreNebPacketFlowStat.Row> rows(FlowSection section) {
            return section == FlowSection.INBOUND ? inboundRows : outboundRows;
        }

        private CoreNebPacketFlowStat.Row row(FlowSection section, int rowIndex) {
            List<CoreNebPacketFlowStat.Row> rows = rows(section);
            return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex) : null;
        }

        private void refresh() {
            CoreNebTrafficStat.Snapshot snapshot = CoreNebTrafficStat.snapshot();
            actualLine = "-> Inbound " + readableSpeed(snapshot.inboundSpeedBaked())
                    + "  Total " + readableSize(snapshot.inboundBytesBaked())
                    + "    -> Outbound " + readableSpeed(snapshot.outboundSpeedBaked())
                    + "  Total " + readableSize(snapshot.outboundBytesBaked());
            rawLine = "-> Inbound " + readableSpeed(snapshot.inboundSpeedRaw())
                    + "  Total " + readableSize(snapshot.inboundBytesRaw())
                    + "    -> Outbound " + readableSpeed(snapshot.outboundSpeedRaw())
                    + "  Total " + readableSize(snapshot.outboundBytesRaw());
            ratioLine = "Ratio (actual/raw) inbound "
                    + ratioPercent(snapshot.inboundBytesBaked(), snapshot.inboundBytesRaw())
                    + "   outbound "
                    + ratioPercent(snapshot.outboundBytesBaked(), snapshot.outboundBytesRaw());
            modeLine = CoreConfig.nebGlobalMixinEnabled()
                    ? "Mode: Global Connection Mixin (ratio = NEB batch only)"
                    : "Mode: CoreNetwork only";
            if (CoreConfig.nebGlobalMixinEnabled()) {
                modeLine += CoreConfig.nebGlobalFullPacketStat()
                        ? " + full packet stat (NEB + BYPASS)"
                        : " + NEB-only packet stat";
            }
            CoreNebPacketFlowStat.Snapshot flowSnapshot = CoreNebPacketFlowStat.snapshot(Integer.MAX_VALUE);
            inboundRows = flowSnapshot.inbound();
            outboundRows = flowSnapshot.outbound();
        }
    }

    private record TableCapacity(int inboundRows, int outboundRows) {
        private static TableCapacity capture(CoreNetworkStatModel model) {
            return new TableCapacity(
                    Math.max(1, model.inboundRows.size()),
                    Math.max(1, model.outboundRows.size())
            );
        }
    }

    private enum FlowSection {
        INBOUND("Download (Inbound)"),
        OUTBOUND("Upload (Outbound)");

        private final String title;

        FlowSection(String title) {
            this.title = title;
        }

        private String title() {
            return title;
        }
    }

    private static final class LiveTextElement implements ElementPainter {
        private final Supplier<String> textSupplier;
        private final IntSupplier colorSupplier;
        private final boolean autoEllipsis;
        private final boolean allowOverflow;
        private String lastText;
        private int lastColor;
        private FizzyComponentElement delegate;

        private LiveTextElement(
                Supplier<String> textSupplier,
                IntSupplier colorSupplier,
                boolean autoEllipsis,
                boolean allowOverflow
        ) {
            this.textSupplier = Objects.requireNonNull(textSupplier, "textSupplier");
            this.colorSupplier = Objects.requireNonNull(colorSupplier, "colorSupplier");
            this.autoEllipsis = autoEllipsis;
            this.allowOverflow = allowOverflow;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
            resolveDelegate().render(guiGraphics, x, y, width, height, partialTick);
        }

        @Override
        public ElementType type() {
            return ElementType.CUSTOM;
        }

        @Override
        public List<AbstractWidget> widgets() {
            return List.of();
        }

        private FizzyComponentElement resolveDelegate() {
            String nextText = Objects.requireNonNullElse(textSupplier.get(), "");
            int nextColor = colorSupplier.getAsInt();
            if (delegate == null || !nextText.equals(lastText) || nextColor != lastColor) {
                FizzyComponentElement.Builder builder = FizzyComponentElement.builder();
                builder.addText(Component.literal(nextText));
                builder.align(TextRenderer.Align.LEFT);
                builder.color(nextColor);
                builder.wrap(false);
                builder.autoEllipsis(autoEllipsis);
                builder.clipToPad(!allowOverflow);
                builder.allowOverflow(allowOverflow);
                delegate = builder.build();
                lastText = nextText;
                lastColor = nextColor;
            }
            return delegate;
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

    private static String ratioPercent(long baked, long raw) {
        if (raw <= 0L) {
            return "N/A";
        }
        return String.format("%.2f%%", 100.0d * baked / raw);
    }

    private static String readableSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1_000.0d) {
            return String.format("%.0f Bytes/s", bytesPerSecond);
        }
        if (bytesPerSecond < 1_000_000.0d) {
            return String.format("%.1f KiB/s", bytesPerSecond / 1_024.0d);
        }
        return String.format("%.2f MiB/s", bytesPerSecond / (1_024.0d * 1_024.0d));
    }

    private static String readableSize(long bytes) {
        if (bytes < 1_000L) {
            return bytes + " Bytes";
        }
        if (bytes < 1_000_000L) {
            return String.format("%.1f KiB", bytes / 1_024.0d);
        }
        if (bytes < 1_000_000_000L) {
            return String.format("%.2f MiB", bytes / (1_024.0d * 1_024.0d));
        }
        return String.format("%.2f GiB", bytes / (1_024.0d * 1_024.0d * 1_024.0d));
    }

    @FunctionalInterface
    private interface RowTextSupplier {
        String get(FlowSection section, int rowIndex);
    }

    @FunctionalInterface
    private interface RowColorSupplier {
        int get(FlowSection section, int rowIndex);
    }
}
