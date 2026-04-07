package link.botwmcs.ltsxassistant.client.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import link.botwmcs.fizzy.client.util.FizzyGuiUtils;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Hover-aware overlay element.
 * Layer order in widgets phase: base widgets -> hover mask -> hover child widgets.
 */
public final class HoverOverlayPadElement implements ElementPainter {
    private final int hoverOverlayColor;
    private final List<PadSpec> pads;
    private final List<ResolvedPad> resolvedPads = new ArrayList<>();
    private final List<AbstractWidget> childWidgets = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private OverlayMaskWidget maskWidget;
    private boolean hovered;
    private int rootX;
    private int rootY;
    private int rootWidth;
    private int rootHeight;

    private HoverOverlayPadElement(Builder builder) {
        this.hoverOverlayColor = builder.hoverOverlayColor;
        this.pads = List.copyOf(builder.pads);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void init(InitContext context, int x, int y, int width, int height) {
        resolvedPads.clear();
        childWidgets.clear();
        widgets.clear();
        rootX = x;
        rootY = y;
        rootWidth = Math.max(1, width);
        rootHeight = Math.max(1, height);

        // Add mask widget first so it renders after base cell content but before hover action widgets.
        maskWidget = context.addRenderableWidget(new OverlayMaskWidget(rootX, rootY, rootWidth, rootHeight));
        maskWidget.active = false;
        widgets.add(maskWidget);

        for (PadSpec spec : pads) {
            int padWidth = spec.fillWidth() ? rootWidth : Math.max(1, spec.width());
            int padHeight = spec.fillHeight() ? rootHeight : Math.max(1, spec.height());
            int padX = rootX + spec.x();
            int padY = rootY + spec.y();

            List<ResolvedChild> children = new ArrayList<>();
            List<AbstractWidget> localWidgets = new ArrayList<>();
            for (ElementPainter child : spec.elements()) {
                List<AbstractWidget> childWidgetRefs = new ArrayList<>();
                child.init(new ChildInitContext(context, childWidgetRefs), padX, padY, padWidth, padHeight);
                localWidgets.addAll(childWidgetRefs);
                children.add(new ResolvedChild(child, childWidgetRefs));
            }

            widgets.addAll(localWidgets);
            childWidgets.addAll(localWidgets);
            resolvedPads.add(new ResolvedPad(spec, children, localWidgets));
        }

        setChildWidgetsVisible(false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        rootX = x;
        rootY = y;
        rootWidth = Math.max(1, width);
        rootHeight = Math.max(1, height);
        if (maskWidget != null) {
            FizzyGuiUtils.syncWidgetBounds(maskWidget, rootX, rootY, rootWidth, rootHeight);
        }

        hovered = isHovered(rootX, rootY, rootWidth, rootHeight);
        setChildWidgetsVisible(hovered);
        if (!hovered) {
            return;
        }

        // Keep widget-based hover children synced every frame.
        for (ResolvedPad resolvedPad : resolvedPads) {
            PadSpec spec = resolvedPad.spec();
            int padWidth = spec.fillWidth() ? rootWidth : Math.max(1, spec.width());
            int padHeight = spec.fillHeight() ? rootHeight : Math.max(1, spec.height());
            int padX = rootX + spec.x();
            int padY = rootY + spec.y();
            for (ResolvedChild child : resolvedPad.children()) {
                if (!child.widgets().isEmpty()) {
                    child.painter().render(guiGraphics, padX, padY, padWidth, padHeight, partialTick);
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

    private boolean isHovered(int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null || minecraft.mouseHandler == null) {
            return false;
        }
        double mouseX = minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth()
                / (double) minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight()
                / (double) minecraft.getWindow().getScreenHeight();
        return mouseX >= x && mouseX < (x + width) && mouseY >= y && mouseY < (y + height);
    }

    private void setChildWidgetsVisible(boolean visible) {
        for (AbstractWidget widget : childWidgets) {
            widget.visible = visible;
            widget.active = visible;
        }
    }

    public static final class Builder {
        private int hoverOverlayColor = 0x5AFFFFFF;
        private final List<PadSpec> pads = new ArrayList<>();

        private Builder() {
        }

        public Builder hoverOverlayColor(int argb) {
            this.hoverOverlayColor = argb;
            return this;
        }

        public Builder element(ElementPainter element) {
            if (element != null) {
                pads.add(new PadSpec(0, 0, 0, 0, true, true, List.of(element)));
            }
            return this;
        }

        public Builder elements(ElementPainter... elements) {
            if (elements == null || elements.length == 0) {
                return this;
            }
            List<ElementPainter> out = new ArrayList<>(elements.length);
            for (ElementPainter element : elements) {
                if (element != null) {
                    out.add(element);
                }
            }
            if (!out.isEmpty()) {
                pads.add(new PadSpec(0, 0, 0, 0, true, true, out));
            }
            return this;
        }

        public Builder padByPx(int x, int y, int width, int height, Consumer<PadBuilder> configurer) {
            PadBuilder builder = new PadBuilder();
            if (configurer != null) {
                configurer.accept(builder);
            }
            List<ElementPainter> children = builder.build();
            if (!children.isEmpty()) {
                pads.add(new PadSpec(
                        x,
                        y,
                        Math.max(1, width),
                        Math.max(1, height),
                        false,
                        false,
                        children
                ));
            }
            return this;
        }

        public HoverOverlayPadElement build() {
            return new HoverOverlayPadElement(this);
        }
    }

    public static final class PadBuilder {
        private final List<ElementPainter> elements = new ArrayList<>();

        public PadBuilder element(ElementPainter element) {
            if (element != null) {
                elements.add(element);
            }
            return this;
        }

        public PadBuilder elements(ElementPainter... elements) {
            if (elements == null || elements.length == 0) {
                return this;
            }
            for (ElementPainter element : elements) {
                if (element != null) {
                    this.elements.add(element);
                }
            }
            return this;
        }

        private List<ElementPainter> build() {
            return List.copyOf(elements);
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

    private record ResolvedPad(PadSpec spec, List<ResolvedChild> children, List<AbstractWidget> widgets) {
    }

    private record ResolvedChild(ElementPainter painter, List<AbstractWidget> widgets) {
    }

    private record ChildInitContext(InitContext parent, List<AbstractWidget> localWidgets) implements InitContext {
        @Override
        public <T extends AbstractWidget> T addRenderableWidget(T widget) {
            T added = parent.addRenderableWidget(widget);
            localWidgets.add(added);
            return added;
        }
    }

    private final class OverlayMaskWidget extends AbstractWidget {
        private OverlayMaskWidget(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (!hovered) {
                return;
            }
            int x1 = getX();
            int y1 = getY();
            int x2 = x1 + getWidth();
            int y2 = y1 + getHeight();
            guiGraphics.fill(x1, y1, x2, y2, hoverOverlayColor);

            // Draw non-widget hover children above the mask.
            for (ResolvedPad resolvedPad : resolvedPads) {
                PadSpec spec = resolvedPad.spec();
                int padWidth = spec.fillWidth() ? rootWidth : Math.max(1, spec.width());
                int padHeight = spec.fillHeight() ? rootHeight : Math.max(1, spec.height());
                int padX = rootX + spec.x();
                int padY = rootY + spec.y();
                for (ResolvedChild child : resolvedPad.children()) {
                    if (child.widgets().isEmpty()) {
                        child.painter().render(guiGraphics, padX, padY, padWidth, padHeight, partialTick);
                    }
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }
}
