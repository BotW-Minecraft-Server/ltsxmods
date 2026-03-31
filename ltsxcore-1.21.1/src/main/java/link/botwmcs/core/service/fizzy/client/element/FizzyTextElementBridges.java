package link.botwmcs.core.service.fizzy.client.element;

import java.util.List;
import java.util.function.Consumer;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.core.api.fizzier.text.IFizzyTextService;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.element.component.FizzyTooltipElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

abstract class AbstractTextBuilder<T, SELF> {
    protected final T delegate;

    protected AbstractTextBuilder(T delegate) {
        this.delegate = delegate;
    }

    protected abstract SELF self();

    protected abstract void rawAddText(Component text);

    protected abstract void rawAddText(String text);

    protected abstract void rawAddText(Component text, Consumer<TextRenderer.Builder<?>> styleConfigurer);

    protected abstract void rawAddTextLines(Component text);

    protected abstract void rawWrap(boolean wrap);

    protected abstract void rawAutoEllipsis(boolean autoEllipsis);

    protected abstract void rawCenterEllipsis(boolean centerEllipsis);

    protected abstract void rawAlign(IFizzyTextService.LtsxTextAlign align);

    protected abstract void rawLineSpacing(float lineSpacing);

    protected abstract void rawTextScale(float textScale);

    protected abstract void rawLetterSpacing(float letterSpacing);

    protected abstract void rawColor(int colorArgb);

    protected abstract void rawShadow(boolean shadow);

    protected abstract void rawClipToPad(boolean clipToPad);

    protected abstract void rawAllowOverflow(boolean allowOverflow);

    protected abstract void rawBold(boolean bold);

    protected abstract void rawUnderline(boolean underline);

    protected abstract void rawStrikethrough(boolean strikethrough);

    protected abstract void rawGradient(int... colors);

    protected abstract void rawRainbow(float speed);

    protected abstract void rawFloating(boolean enabled, float amplitude, float speed);

    protected abstract void rawFloatingPixelated(boolean pixelated);

    public SELF addText(Component text) {
        rawAddText(text);
        return self();
    }

    public SELF addText(String text) {
        rawAddText(text);
        return self();
    }

    public SELF addText(Component text, Consumer<IFizzyTextService.LtsxTextRendererBuilder> styleConfigurer) {
        rawAddText(text, raw -> FizzyElementService.configure(styleConfigurer, new TextRendererStyleBuilderBridge(raw)));
        return self();
    }

    public SELF addTextLines(Component text) {
        rawAddTextLines(text);
        return self();
    }

    public SELF wrap(boolean wrap) {
        rawWrap(wrap);
        return self();
    }

    public SELF autoEllipsis(boolean autoEllipsis) {
        rawAutoEllipsis(autoEllipsis);
        return self();
    }

    public SELF centerEllipsis(boolean centerEllipsis) {
        rawCenterEllipsis(centerEllipsis);
        return self();
    }

    public SELF align(IFizzyTextService.LtsxTextAlign align) {
        rawAlign(align);
        return self();
    }

    public SELF lineSpacing(float lineSpacing) {
        rawLineSpacing(lineSpacing);
        return self();
    }

    public SELF textScale(float textScale) {
        rawTextScale(textScale);
        return self();
    }

    public SELF letterSpacing(float letterSpacing) {
        rawLetterSpacing(letterSpacing);
        return self();
    }

    public SELF color(int colorArgb) {
        rawColor(colorArgb);
        return self();
    }

    public SELF shadow(boolean shadow) {
        rawShadow(shadow);
        return self();
    }

    public SELF clipToPad(boolean clipToPad) {
        rawClipToPad(clipToPad);
        return self();
    }

    public SELF allowOverflow(boolean allowOverflow) {
        rawAllowOverflow(allowOverflow);
        return self();
    }

    public SELF bold(boolean bold) {
        rawBold(bold);
        return self();
    }

    public SELF underline(boolean underline) {
        rawUnderline(underline);
        return self();
    }

    public SELF strikethrough(boolean strikethrough) {
        rawStrikethrough(strikethrough);
        return self();
    }

    public SELF gradient(int... colors) {
        rawGradient(colors);
        return self();
    }

    public SELF rainbow() {
        rawRainbow(1.0F);
        return self();
    }

    public SELF rainbow(float speed) {
        rawRainbow(speed);
        return self();
    }

    public SELF floating(boolean enabled, float amplitude, float speed) {
        rawFloating(enabled, amplitude, speed);
        return self();
    }

    public SELF floatingPixelated(boolean pixelated) {
        rawFloatingPixelated(pixelated);
        return self();
    }
}

final class ComponentBuilderBridge
        extends AbstractTextBuilder<FizzyComponentElement.Builder, IFizzyElementService.LtsxComponentBuilder>
        implements IFizzyElementService.LtsxComponentBuilder {
    ComponentBuilderBridge(FizzyComponentElement.Builder delegate) {
        super(delegate);
    }

    @Override
    protected IFizzyElementService.LtsxComponentBuilder self() {
        return this;
    }

    @Override
    protected void rawAddText(Component text) {
        delegate.addText(text);
    }

    @Override
    protected void rawAddText(String text) {
        delegate.addText(text);
    }

    @Override
    protected void rawAddText(Component text, Consumer<TextRenderer.Builder<?>> styleConfigurer) {
        delegate.addText(text, styleConfigurer);
    }

    @Override
    protected void rawAddTextLines(Component text) {
        delegate.addTextLines(text);
    }

    @Override
    protected void rawWrap(boolean wrap) {
        delegate.wrap(wrap);
    }

    @Override
    protected void rawAutoEllipsis(boolean autoEllipsis) {
        delegate.autoEllipsis(autoEllipsis);
    }

    @Override
    protected void rawCenterEllipsis(boolean centerEllipsis) {
        delegate.centerEllipsis(centerEllipsis);
    }

    @Override
    protected void rawAlign(IFizzyTextService.LtsxTextAlign align) {
        delegate.align(FizzyElementService.mapAlign(align));
    }

    @Override
    protected void rawLineSpacing(float lineSpacing) {
        delegate.lineSpacing(lineSpacing);
    }

    @Override
    protected void rawTextScale(float textScale) {
        delegate.textScale(textScale);
    }

    @Override
    protected void rawLetterSpacing(float letterSpacing) {
        delegate.letterSpacing(letterSpacing);
    }

    @Override
    protected void rawColor(int colorArgb) {
        delegate.color(colorArgb);
    }

    @Override
    protected void rawShadow(boolean shadow) {
        delegate.shadow(shadow);
    }

    @Override
    protected void rawClipToPad(boolean clipToPad) {
        delegate.clipToPad(clipToPad);
    }

    @Override
    protected void rawAllowOverflow(boolean allowOverflow) {
        delegate.allowOverflow(allowOverflow);
    }

    @Override
    protected void rawBold(boolean bold) {
        delegate.bold(bold);
    }

    @Override
    protected void rawUnderline(boolean underline) {
        delegate.underline(underline);
    }

    @Override
    protected void rawStrikethrough(boolean strikethrough) {
        delegate.strikethrough(strikethrough);
    }

    @Override
    protected void rawGradient(int... colors) {
        delegate.gradient(colors);
    }

    @Override
    protected void rawRainbow(float speed) {
        delegate.rainbow(speed);
    }

    @Override
    protected void rawFloating(boolean enabled, float amplitude, float speed) {
        delegate.floating(enabled, amplitude, speed);
    }

    @Override
    protected void rawFloatingPixelated(boolean pixelated) {
        delegate.floatingPixelated(pixelated);
    }
}

final class TooltipBuilderBridge
        extends AbstractTextBuilder<FizzyTooltipElement.Builder, IFizzyElementService.LtsxTooltipBuilder>
        implements IFizzyElementService.LtsxTooltipBuilder {
    TooltipBuilderBridge(FizzyTooltipElement.Builder delegate) {
        super(delegate);
    }

    @Override
    protected IFizzyElementService.LtsxTooltipBuilder self() {
        return this;
    }

    @Override
    protected void rawAddText(Component text) {
        delegate.addText(text);
    }

    @Override
    protected void rawAddText(String text) {
        delegate.addText(text);
    }

    @Override
    protected void rawAddText(Component text, Consumer<TextRenderer.Builder<?>> styleConfigurer) {
        delegate.addText(text, styleConfigurer);
    }

    @Override
    protected void rawAddTextLines(Component text) {
        delegate.addTextLines(text);
    }

    @Override
    protected void rawWrap(boolean wrap) {
        delegate.wrap(wrap);
    }

    @Override
    protected void rawAutoEllipsis(boolean autoEllipsis) {
    }

    @Override
    protected void rawCenterEllipsis(boolean centerEllipsis) {
    }

    @Override
    protected void rawAlign(IFizzyTextService.LtsxTextAlign align) {
        delegate.align(FizzyElementService.mapAlign(align));
    }

    @Override
    protected void rawLineSpacing(float lineSpacing) {
        delegate.lineSpacing(lineSpacing);
    }

    @Override
    protected void rawTextScale(float textScale) {
        delegate.textScale(textScale);
    }

    @Override
    protected void rawLetterSpacing(float letterSpacing) {
        delegate.letterSpacing(letterSpacing);
    }

    @Override
    protected void rawColor(int colorArgb) {
        delegate.color(colorArgb);
    }

    @Override
    protected void rawShadow(boolean shadow) {
        delegate.shadow(shadow);
    }

    @Override
    protected void rawClipToPad(boolean clipToPad) {
        delegate.clipToPad(clipToPad);
    }

    @Override
    protected void rawAllowOverflow(boolean allowOverflow) {
        delegate.allowOverflow(allowOverflow);
    }

    @Override
    protected void rawBold(boolean bold) {
        delegate.bold(bold);
    }

    @Override
    protected void rawUnderline(boolean underline) {
        delegate.underline(underline);
    }

    @Override
    protected void rawStrikethrough(boolean strikethrough) {
        delegate.strikethrough(strikethrough);
    }

    @Override
    protected void rawGradient(int... colors) {
        delegate.gradient(colors);
    }

    @Override
    protected void rawRainbow(float speed) {
        delegate.rainbow(speed);
    }

    @Override
    protected void rawFloating(boolean enabled, float amplitude, float speed) {
        delegate.floating(enabled, amplitude, speed);
    }

    @Override
    protected void rawFloatingPixelated(boolean pixelated) {
        delegate.floatingPixelated(pixelated);
    }

    @Override
    public IFizzyElementService.LtsxTooltipBuilder maxWidthPx(int maxWidthPx) {
        delegate.maxWidthPx(maxWidthPx);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxTooltipBuilder tooltipColors(
            int backgroundTop,
            int backgroundBottom,
            int borderTop,
            int borderBottom
    ) {
        delegate.tooltipColors(backgroundTop, backgroundBottom, borderTop, borderBottom);
        return this;
    }
}

final class ProgressBuilderBridge implements IFizzyElementService.LtsxProgressBuilder {
    private final link.botwmcs.fizzy.ui.element.funstuff.vector.ProgressElement.Builder delegate;

    ProgressBuilderBridge(link.botwmcs.fizzy.ui.element.funstuff.vector.ProgressElement.Builder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxProgressBuilder progress(float progress) {
        delegate.progress(progress);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxProgressBuilder color(IFizzyElementService.LtsxProgressColor color) {
        delegate.color(FizzyElementService.mapProgressColor(color));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxProgressBuilder barHeight(int barHeight) {
        delegate.barHeight(barHeight);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxProgressBuilder autoNotches(boolean autoNotches) {
        delegate.autoNotches(autoNotches);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxProgressBuilder minNotchSegmentWidthPx(int widthPx) {
        delegate.minNotchSegmentWidthPx(widthPx);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxProgressBuilder capWidthPx(int widthPx) {
        delegate.capWidthPx(widthPx);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxProgressBuilder addNotch(int px) {
        delegate.addNotch(px);
        return this;
    }
}

final class TextRendererStyleBuilderBridge implements IFizzyTextService.LtsxTextRendererBuilder {
    private final TextRenderer.Builder<?> delegate;

    TextRendererStyleBuilderBridge(TextRenderer.Builder<?> delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder text(Component text) {
        delegate.text(text);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder lines(List<Component> lines) {
        delegate.lines(lines);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder singleLine() {
        delegate.singleLine();
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder multiLine() {
        delegate.multiLine();
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder wrap(boolean wrap) {
        delegate.wrap(wrap);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder align(IFizzyTextService.LtsxTextAlign align) {
        delegate.align(FizzyElementService.mapAlign(align));
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder textScale(float textScale) {
        delegate.textScale(textScale);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder lineSpacing(float lineSpacing) {
        delegate.lineSpacing(lineSpacing);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder letterSpacing(float letterSpacing) {
        delegate.letterSpacing(letterSpacing);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder color(int colorArgb) {
        delegate.color(colorArgb);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder shadow(boolean shadow) {
        delegate.shadow(shadow);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder clipToPad(boolean clipToPad) {
        delegate.clipToPad(clipToPad);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder allowOverflow(boolean allowOverflow) {
        delegate.allowOverflow(allowOverflow);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder bold(boolean bold) {
        delegate.bold(bold);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder underline(boolean underline) {
        delegate.underline(underline);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder strikethrough(boolean strikethrough) {
        delegate.strikethrough(strikethrough);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder gradient(int... colors) {
        delegate.gradient(colors);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder rainbow() {
        delegate.rainbow();
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder rainbow(float speed) {
        delegate.rainbow(speed);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder floating(boolean enabled, float amplitude, float speed) {
        delegate.floating(enabled, amplitude, speed);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRendererBuilder floatingPixelated(boolean pixelated) {
        delegate.floatingPixelated(pixelated);
        return this;
    }

    @Override
    public IFizzyTextService.LtsxTextRenderer build() {
        return new TextRendererBridge(delegate.buildRenderer());
    }
}

record TextRendererBridge(TextRenderer delegate) implements IFizzyTextService.LtsxTextRenderer {
    @Override
    public IFizzyTextService.LtsxTextMetrics measure(Font font, int maxWidthPx) {
        TextRenderer.TextMetrics metrics = delegate.measure(font, maxWidthPx);
        return new IFizzyTextService.LtsxTextMetrics(metrics.lines(), metrics.maxWidth(), metrics.totalHeight());
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick) {
        delegate.render(graphics, x, y, width, height, partialTick);
    }
}
