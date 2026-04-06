package link.botwmcs.ltsxassistant.service.client.elements;

import java.util.List;
import java.util.Objects;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Badge component element.
 * Draws a bordered badge and renders centered text using {@link FizzyComponentElement}.
 */
public final class BadgeComponentElement implements ElementPainter {
    private static final int DEFAULT_OUTLINE_COLOR = 0xFF5A5A5A;
    private static final int DEFAULT_FILL_COLOR = 0xCC2B2B2B;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int MIN_TAG_WIDTH_PX = 6;

    private final Component text;
    private final int outlineColor;
    private final int fillColor;
    private final int textColor;
    private final int fixedTagWidthPx;
    private final FizzyComponentElement textElement;

    private BadgeComponentElement(Builder builder) {
        this.text = Objects.requireNonNullElse(builder.text, Component.empty());
        this.outlineColor = builder.outlineColor;
        this.fillColor = builder.fillColor;
        this.textColor = builder.textColor;
        this.fixedTagWidthPx = builder.fixedTagWidthPx;
        this.textElement = new FizzyComponentElement.Builder()
                .addText(this.text)
                .align(TextRenderer.Align.CENTER)
                .wrap(false)
                .autoEllipsis(true)
                .shadow(builder.shadow)
                .color(this.textColor)
                .clipToPad(true)
                .allowOverflow(false)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Draw badge with original signature style (formatted sequence input), but text rendering now uses FizzyComponentElement.
     */
    public static void drawBadge(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int tagWidth,
            FormattedCharSequence charSequence,
            int outlineColor,
            int fillColor,
            int textColor
    ) {
        Component text = Component.literal(toPlainString(charSequence));
        FizzyComponentElement textElement = new FizzyComponentElement.Builder()
                .addText(text)
                .align(TextRenderer.Align.CENTER)
                .wrap(false)
                .autoEllipsis(true)
                .shadow(false)
                .color(textColor)
                .clipToPad(true)
                .allowOverflow(false)
                .build();
        drawBadge(guiGraphics, x, y, tagWidth, outlineColor, fillColor, textElement, 0.0F);
    }

    /**
     * Draw badge with a component payload, rendered via {@link FizzyComponentElement}.
     */
    public static void drawBadge(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int tagWidth,
            Component text,
            int outlineColor,
            int fillColor,
            int textColor
    ) {
        FizzyComponentElement textElement = new FizzyComponentElement.Builder()
                .addText(Objects.requireNonNullElse(text, Component.empty()))
                .align(TextRenderer.Align.CENTER)
                .wrap(false)
                .autoEllipsis(true)
                .shadow(false)
                .color(textColor)
                .clipToPad(true)
                .allowOverflow(false)
                .build();
        drawBadge(guiGraphics, x, y, tagWidth, outlineColor, fillColor, textElement, 0.0F);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        if (width <= 1 || height <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int maxTagWidth = Math.max(1, width - 1);
        int autoTagWidth = Math.max(MIN_TAG_WIDTH_PX, font.width(text) + 2);
        int tagWidth = fixedTagWidthPx > 0 ? fixedTagWidthPx : autoTagWidth;
        tagWidth = Math.min(maxTagWidth, Math.max(1, tagWidth));

        // Keep the top border inside the provided pad by offsetting y by +1.
        drawBadge(guiGraphics, x, y + 1, tagWidth, outlineColor, fillColor, textElement, partialTick);
    }

    @Override
    public ElementType type() {
        return ElementType.CUSTOM;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return List.of();
    }

    private static void drawBadge(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int tagWidth,
            int outlineColor,
            int fillColor,
            FizzyComponentElement textElement,
            float partialTick
    ) {
        if (tagWidth <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight;

        guiGraphics.fill(x + 1, y - 1, x + tagWidth, y, outlineColor);
        guiGraphics.fill(x, y, x + 1, y + lineHeight, outlineColor);
        guiGraphics.fill(x + 1, y + lineHeight, x + tagWidth, y + lineHeight + 1, outlineColor);
        guiGraphics.fill(x + tagWidth, y, x + tagWidth + 1, y + lineHeight, outlineColor);
        guiGraphics.fill(x + 1, y, x + tagWidth, y + lineHeight, fillColor);

        textElement.render(guiGraphics, x + 1, y + 1, tagWidth, lineHeight, partialTick);
    }

    private static String toPlainString(FormattedCharSequence charSequence) {
        if (charSequence == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        charSequence.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    public static final class Builder {
        private Component text = Component.empty();
        private int outlineColor = DEFAULT_OUTLINE_COLOR;
        private int fillColor = DEFAULT_FILL_COLOR;
        private int textColor = DEFAULT_TEXT_COLOR;
        private int fixedTagWidthPx = -1;
        private boolean shadow;

        private Builder() {
        }

        public Builder text(Component text) {
            this.text = Objects.requireNonNullElse(text, Component.empty());
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

        public Builder tagWidthPx(int tagWidthPx) {
            this.fixedTagWidthPx = tagWidthPx;
            return this;
        }

        public Builder shadow(boolean shadow) {
            this.shadow = shadow;
            return this;
        }

        public BadgeComponentElement build() {
            return new BadgeComponentElement(this);
        }
    }
}
