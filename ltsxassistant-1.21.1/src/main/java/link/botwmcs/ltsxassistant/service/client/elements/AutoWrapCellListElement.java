package link.botwmcs.ltsxassistant.service.client.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Auto-wrapping cell list element.
 * Cells are placed from left to right, then top to bottom.
 */
public final class AutoWrapCellListElement implements ElementPainter {
    private static final int DEFAULT_GAP_PX = 6;
    private final List<CellSpec> cells;
    private final int horizontalGapPx;
    private final int verticalGapPx;
    private final int paddingLeftPx;
    private final int paddingTopPx;
    private final int paddingRightPx;
    private final int paddingBottomPx;
    private final RowAlign rowAlign;
    private final List<ResolvedCell> resolvedCells = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();

    private AutoWrapCellListElement(Builder builder) {
        this.cells = List.copyOf(builder.cells);
        this.horizontalGapPx = builder.horizontalGapPx;
        this.verticalGapPx = builder.verticalGapPx;
        this.paddingLeftPx = builder.paddingLeftPx;
        this.paddingTopPx = builder.paddingTopPx;
        this.paddingRightPx = builder.paddingRightPx;
        this.paddingBottomPx = builder.paddingBottomPx;
        this.rowAlign = builder.rowAlign;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void init(InitContext context, int x, int y, int width, int height) {
        resolvedCells.clear();
        widgets.clear();
        Layout layout = computeLayout(x, y, width);
        for (int i = 0; i < cells.size(); i++) {
            CellSpec cell = cells.get(i);
            LayoutCell layoutCell = layout.cells.get(i);
            List<ResolvedPad> resolvedPads = new ArrayList<>();
            for (PadSpec pad : cell.pads()) {
                int padX = layoutCell.x() + pad.x();
                int padY = layoutCell.y() + pad.y();
                int padW = pad.fillWidth() ? layoutCell.width() : Math.max(1, pad.width());
                int padH = pad.fillHeight() ? layoutCell.height() : Math.max(1, pad.height());
                List<ResolvedElement> resolvedElements = new ArrayList<>();
                for (ElementPainter painter : pad.elements()) {
                    List<AbstractWidget> localWidgets = new ArrayList<>();
                    painter.init(new ChildInitContext(context, localWidgets), padX, padY, padW, padH);
                    widgets.addAll(localWidgets);
                    resolvedElements.add(new ResolvedElement(painter, localWidgets));
                }
                resolvedPads.add(new ResolvedPad(pad, resolvedElements));
            }
            resolvedCells.add(new ResolvedCell(cell, resolvedPads));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        if (resolvedCells.isEmpty()) {
            return;
        }
        Layout layout = computeLayout(x, y, width);
        int resolvedCount = Math.min(layout.cells.size(), resolvedCells.size());
        for (int i = 0; i < resolvedCount; i++) {
            LayoutCell layoutCell = layout.cells.get(i);
            ResolvedCell resolvedCell = resolvedCells.get(i);
            for (ResolvedPad resolvedPad : resolvedCell.pads()) {
                PadSpec pad = resolvedPad.spec();
                int padX = layoutCell.x() + pad.x();
                int padY = layoutCell.y() + pad.y();
                int padW = pad.fillWidth() ? layoutCell.width() : Math.max(1, pad.width());
                int padH = pad.fillHeight() ? layoutCell.height() : Math.max(1, pad.height());
                for (ResolvedElement resolvedElement : resolvedPad.elements()) {
                    resolvedElement.painter().render(guiGraphics, padX, padY, padW, padH, partialTick);
                }
            }
        }
    }

    @Override
    public ElementType type() {
        return ElementType.CUSTOM;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return widgets;
    }

    public int measureContentHeight(int viewportWidth) {
        return computeLayout(0, 0, viewportWidth).contentHeight();
    }

    private Layout computeLayout(int x, int y, int width) {
        if (cells.isEmpty()) {
            return new Layout(List.of(), Math.max(1, paddingTopPx + paddingBottomPx));
        }

        LayoutCell[] out = new LayoutCell[cells.size()];
        int availableWidth = Math.max(1, width - paddingLeftPx - paddingRightPx);
        int contentStartX = x + paddingLeftPx;
        List<RowLayoutData> rows = new ArrayList<>();
        RowLayoutData currentRow = new RowLayoutData();

        for (int index = 0; index < cells.size(); index++) {
            CellSpec cell = cells.get(index);
            int cellWidth = Math.min(Math.max(1, cell.width()), availableWidth);
            int cellHeight = Math.max(1, cell.height());
            if (!currentRow.items().isEmpty()) {
                int predicted = currentRow.usedWidth() + horizontalGapPx + cellWidth;
                if (predicted > availableWidth) {
                    rows.add(currentRow);
                    currentRow = new RowLayoutData();
                }
            }
            currentRow.items().add(new RowItem(index, cellWidth, cellHeight));
            currentRow.usedWidth = currentRow.items().size() == 1
                    ? cellWidth
                    : currentRow.usedWidth() + horizontalGapPx + cellWidth;
            currentRow.rowHeight = Math.max(currentRow.rowHeight(), cellHeight);
        }
        if (!currentRow.items().isEmpty()) {
            rows.add(currentRow);
        }

        int rowTopY = y + paddingTopPx;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            RowLayoutData row = rows.get(rowIndex);
            int count = row.items().size();
            int totalCellWidth = 0;
            for (RowItem item : row.items()) {
                totalCellWidth += item.width();
            }

            int baseGap = horizontalGapPx;
            int extraGapRemainder = 0;
            int startX = contentStartX;
            if (rowAlign == RowAlign.CENTER) {
                int rowWidth = totalCellWidth + horizontalGapPx * Math.max(0, count - 1);
                startX = contentStartX + Math.max(0, (availableWidth - rowWidth) / 2);
            } else if (rowAlign == RowAlign.JUSTIFY && count > 1) {
                int freeSpace = Math.max(0, availableWidth - totalCellWidth);
                baseGap = freeSpace / (count - 1);
                extraGapRemainder = freeSpace % (count - 1);
            }

            int cursorX = startX;
            for (int i = 0; i < count; i++) {
                RowItem item = row.items().get(i);
                out[item.index()] = new LayoutCell(cursorX, rowTopY, item.width(), item.height());
                if (i < count - 1) {
                    int gap = baseGap;
                    if (rowAlign == RowAlign.JUSTIFY && extraGapRemainder > 0) {
                        gap++;
                        extraGapRemainder--;
                    }
                    cursorX += item.width() + gap;
                }
            }

            if (rowIndex < rows.size() - 1) {
                rowTopY += row.rowHeight() + verticalGapPx;
            } else {
                rowTopY += row.rowHeight();
            }
        }

        int contentHeight = Math.max(1, rowTopY - y + paddingBottomPx);
        List<LayoutCell> layoutCells = new ArrayList<>(out.length);
        for (LayoutCell cell : out) {
            layoutCells.add(cell);
        }
        return new Layout(layoutCells, contentHeight);
    }

    public static final class Builder {
        private final List<CellSpec> cells = new ArrayList<>();
        private int horizontalGapPx = DEFAULT_GAP_PX;
        private int verticalGapPx = DEFAULT_GAP_PX;
        private int paddingLeftPx;
        private int paddingTopPx;
        private int paddingRightPx;
        private int paddingBottomPx;
        private RowAlign rowAlign = RowAlign.LEFT;

        private Builder() {
        }

        public Builder gapPx(int horizontal, int vertical) {
            this.horizontalGapPx = Math.max(0, horizontal);
            this.verticalGapPx = Math.max(0, vertical);
            return this;
        }

        public Builder paddingPx(int left, int top, int right, int bottom) {
            this.paddingLeftPx = Math.max(0, left);
            this.paddingTopPx = Math.max(0, top);
            this.paddingRightPx = Math.max(0, right);
            this.paddingBottomPx = Math.max(0, bottom);
            return this;
        }

        public Builder rowAlign(RowAlign align) {
            this.rowAlign = Objects.requireNonNull(align, "align");
            return this;
        }

        public Builder cell(Consumer<CellBuilder> configurer) {
            CellBuilder builder = new CellBuilder();
            if (configurer != null) {
                configurer.accept(builder);
            }
            cells.add(builder.build());
            return this;
        }

        public AutoWrapCellListElement build() {
            return new AutoWrapCellListElement(this);
        }
    }

    public static final class CellBuilder {
        private int width = 120;
        private int height = 140;
        private final List<PadSpec> pads = new ArrayList<>();

        public CellBuilder sizePx(int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            return this;
        }

        public CellBuilder element(ElementPainter element) {
            return elements(element);
        }

        public CellBuilder elements(ElementPainter... elements) {
            ElementPainter[] safeElements = sanitizeElements(elements);
            if (safeElements.length > 0) {
                pads.add(new PadSpec(0, 0, 0, 0, true, true, List.of(safeElements)));
            }
            return this;
        }

        public CellBuilder padByPx(int x, int y, int width, int height, Consumer<CellPadBuilder> configurer) {
            CellPadBuilder padBuilder = new CellPadBuilder();
            if (configurer != null) {
                configurer.accept(padBuilder);
            }
            List<ElementPainter> elements = padBuilder.build();
            if (!elements.isEmpty()) {
                pads.add(new PadSpec(
                        x,
                        y,
                        Math.max(1, width),
                        Math.max(1, height),
                        false,
                        false,
                        elements
                ));
            }
            return this;
        }

        private CellSpec build() {
            if (pads.isEmpty()) {
                pads.add(new PadSpec(0, 0, 0, 0, true, true, List.of()));
            }
            return new CellSpec(width, height, List.copyOf(pads));
        }
    }

    public static final class CellPadBuilder {
        private final List<ElementPainter> elements = new ArrayList<>();

        public CellPadBuilder element(ElementPainter element) {
            if (element != null) {
                elements.add(element);
            }
            return this;
        }

        public CellPadBuilder elements(ElementPainter... elements) {
            for (ElementPainter element : sanitizeElements(elements)) {
                this.elements.add(element);
            }
            return this;
        }

        private List<ElementPainter> build() {
            return List.copyOf(elements);
        }
    }

    private static ElementPainter[] sanitizeElements(ElementPainter... elements) {
        if (elements == null || elements.length == 0) {
            return new ElementPainter[0];
        }
        List<ElementPainter> out = new ArrayList<>(elements.length);
        for (ElementPainter element : elements) {
            if (element != null) {
                out.add(element);
            }
        }
        return out.toArray(new ElementPainter[0]);
    }

    private record CellSpec(int width, int height, List<PadSpec> pads) {
        private CellSpec {
            Objects.requireNonNull(pads, "pads");
        }
    }

    private record PadSpec(
            int x,
            int y,
            int width,
            int height,
            boolean fillWidth,
            boolean fillHeight,
            List<ElementPainter> elements
    ) {
        private PadSpec {
            Objects.requireNonNull(elements, "elements");
        }
    }

    private record Layout(List<LayoutCell> cells, int contentHeight) {
    }

    private record LayoutCell(int x, int y, int width, int height) {
    }

    private static final class RowLayoutData {
        private final List<RowItem> items = new ArrayList<>();
        private int usedWidth;
        private int rowHeight;

        private List<RowItem> items() {
            return items;
        }

        private int usedWidth() {
            return usedWidth;
        }

        private int rowHeight() {
            return rowHeight;
        }
    }

    private record RowItem(int index, int width, int height) {
    }

    public enum RowAlign {
        LEFT,
        CENTER,
        JUSTIFY
    }

    private record ResolvedCell(CellSpec spec, List<ResolvedPad> pads) {
    }

    private record ResolvedPad(PadSpec spec, List<ResolvedElement> elements) {
    }

    private record ResolvedElement(ElementPainter painter, List<AbstractWidget> widgets) {
    }

    private record ChildInitContext(InitContext parent, List<AbstractWidget> localWidgets) implements InitContext {
        @Override
        public <T extends AbstractWidget> T addRenderableWidget(T widget) {
            T added = parent.addRenderableWidget(widget);
            localWidgets.add(added);
            return added;
        }
    }
}
