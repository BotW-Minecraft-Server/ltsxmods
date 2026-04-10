package link.botwmcs.ltsxassistant.client.elements;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import link.botwmcs.fizzy.client.util.FizzyGuiUtils;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Clickable badge button element with the same visual style as {@link BadgeComponentElement}.
 */
public final class BadgeButtonElement implements ElementPainter {
    private static final int DEFAULT_OUTLINE_COLOR = 0xFF5A5A5A;
    private static final int DEFAULT_FILL_COLOR = 0xCC2B2B2B;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_DISABLED_TEXT_COLOR = 0xFF9A9A9A;
    private static final int MIN_TAG_WIDTH_PX = 6;

    private final Runnable onPress;
    private final Component text;
    private final Component narration;
    @Nullable
    private final Tooltip tooltip;
    private final int outlineColor;
    private final int fillColor;
    private final int textColor;
    private final int disabledTextColor;
    private final int fixedTagWidthPx;
    private final boolean shadow;
    private final boolean activeByDefault;
    private final boolean visibleByDefault;

    @Nullable
    private BadgeWidget widget;

    private BadgeButtonElement(Builder builder) {
        this.onPress = Objects.requireNonNull(builder.onPress, "onPress");
        this.text = Objects.requireNonNullElse(builder.text, Component.empty());
        this.narration = Objects.requireNonNullElse(builder.narration, this.text);
        this.tooltip = builder.tooltip == null ? null : Tooltip.create(builder.tooltip);
        this.outlineColor = builder.outlineColor;
        this.fillColor = builder.fillColor;
        this.textColor = builder.textColor;
        this.disabledTextColor = builder.disabledTextColor;
        this.fixedTagWidthPx = builder.fixedTagWidthPx;
        this.shadow = builder.shadow;
        this.activeByDefault = builder.activeByDefault;
        this.visibleByDefault = builder.visibleByDefault;
    }

    public static Builder builder(Runnable onPress) {
        return new Builder(onPress);
    }

    @Override
    public void init(InitContext context, int x, int y, int width, int height) {
        this.widget = context.addRenderableWidget(new BadgeWidget(x, y, Math.max(1, width), Math.max(1, height), narration));
        this.widget.active = activeByDefault;
        this.widget.visible = visibleByDefault;
        this.widget.setTooltip(tooltip);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        if (widget == null) {
            return;
        }
        FizzyGuiUtils.syncWidgetBounds(widget, x, y, Math.max(1, width), Math.max(1, height));
    }

    @Override
    public ElementType type() {
        return ElementType.BUTTON;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return widget == null ? List.of() : List.of(widget);
    }

    public static final class Builder {
        private final Runnable onPress;
        private Component text = Component.empty();
        private Component narration = Component.empty();
        private boolean narrationCustomized;
        @Nullable
        private Component tooltip;
        private int outlineColor = DEFAULT_OUTLINE_COLOR;
        private int fillColor = DEFAULT_FILL_COLOR;
        private int textColor = DEFAULT_TEXT_COLOR;
        private int disabledTextColor = DEFAULT_DISABLED_TEXT_COLOR;
        private int fixedTagWidthPx = -1;
        private boolean shadow;
        private boolean activeByDefault = true;
        private boolean visibleByDefault = true;

        private Builder(Runnable onPress) {
            this.onPress = Objects.requireNonNull(onPress, "onPress");
        }

        public Builder text(Component text) {
            this.text = Objects.requireNonNullElse(text, Component.empty());
            if (!this.narrationCustomized) {
                this.narration = this.text;
            }
            return this;
        }

        public Builder narration(Component narration) {
            this.narration = Objects.requireNonNullElse(narration, Component.empty());
            this.narrationCustomized = true;
            return this;
        }

        public Builder tooltip(Component tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder outlineColor(int outlineColor) {
            this.outlineColor = outlineColor;
            return this;
        }

        public Builder fillColor(int fillColor) {
            this.fillColor = fillColor;
            return this;
        }

        public Builder textColor(int textColor) {
            this.textColor = textColor;
            return this;
        }

        public Builder disabledTextColor(int disabledTextColor) {
            this.disabledTextColor = disabledTextColor;
            return this;
        }

        public Builder tagWidthPx(int tagWidthPx) {
            this.fixedTagWidthPx = tagWidthPx;
            return this;
        }

        public Builder shadow(boolean shadow) {
            this.shadow = shadow;
            return this;
        }

        public Builder active(boolean active) {
            this.activeByDefault = active;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visibleByDefault = visible;
            return this;
        }

        public BadgeButtonElement build() {
            return new BadgeButtonElement(this);
        }
    }

    private final class BadgeWidget extends AbstractWidget {
        private BadgeWidget(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int maxTagWidth = Math.max(1, getWidth() - 1);
            int autoTagWidth = Math.max(MIN_TAG_WIDTH_PX, Minecraft.getInstance().font.width(text) + 2);
            int tagWidth = fixedTagWidthPx > 0 ? fixedTagWidthPx : autoTagWidth;
            tagWidth = Math.min(maxTagWidth, Math.max(1, tagWidth));
            int contentTextColor = this.active ? textColor : disabledTextColor;
            BadgeComponentElement.drawBadge(
                    guiGraphics,
                    getX(),
                    getY() + 1,
                    tagWidth,
                    text,
                    outlineColor,
                    fillColor,
                    contentTextColor
            );
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (active) {
                onPress.run();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
