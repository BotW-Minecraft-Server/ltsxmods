package link.botwmcs.core.service.fizzy.client.element;

import java.util.function.Consumer;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.fizzy.ui.element.button.ColoredButtonElement;
import link.botwmcs.fizzy.ui.element.button.FizzyButtonElement;
import link.botwmcs.fizzy.ui.element.button.IconButtonElement;
import link.botwmcs.fizzy.ui.element.button.TransparentButtonElement;
import link.botwmcs.fizzy.ui.element.button.VanillaLikeButtonElement;
import link.botwmcs.fizzy.ui.element.button.WidgetButtonElement;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.element.component.FizzyTooltipElement;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

abstract class AbstractRichButtonBuilder<T, SELF> {
    protected final T delegate;

    protected AbstractRichButtonBuilder(T delegate) {
        this.delegate = delegate;
    }

    protected abstract SELF self();

    protected abstract void setTooltip(Tooltip tooltip);

    protected abstract void setTooltip(Component tooltip);

    protected abstract void setTooltip(FizzyTooltipElement tooltip);

    protected abstract void setPressSound(SoundEvent soundEvent);

    protected abstract void setNarration(Component narration);

    protected abstract void setText(Component text);

    protected abstract void setTextConfig(Consumer<FizzyComponentElement.Builder> textConfigurer);

    protected abstract void setIcon(ResourceLocation iconTexture);

    protected abstract void setIcon(ResourceLocation iconTexture, boolean stretchToFit, boolean allowUpscale);

    protected abstract void setIconSizePx(int sizePx);

    protected abstract void setContentGapPx(int gapPx);

    protected abstract void setContentPaddingPx(int horizontalPx, int verticalPx);

    protected abstract void setLayout(IFizzyElementService.LtsxButtonContentLayout layout);

    protected abstract void setIconAlign(IFizzyElementService.LtsxIconVerticalAlign align);

    public SELF tooltip(Tooltip tooltip) {
        setTooltip(tooltip);
        return self();
    }

    public SELF tooltip(Component tooltip) {
        setTooltip(tooltip);
        return self();
    }

    public SELF tooltip(IFizzyElementService.LtsxElement tooltipElement) {
        setTooltip(FizzyElementService.unwrapTooltip(tooltipElement));
        return self();
    }

    public SELF pressSound(SoundEvent soundEvent) {
        setPressSound(soundEvent);
        return self();
    }

    public SELF narration(Component narration) {
        setNarration(narration);
        return self();
    }

    public SELF text(Component text) {
        setText(text);
        return self();
    }

    public SELF text(Consumer<IFizzyElementService.LtsxComponentBuilder> textConfigurer) {
        setTextConfig(builder -> FizzyElementService.configure(textConfigurer, new ComponentBuilderBridge(builder)));
        return self();
    }

    public SELF icon(ResourceLocation iconTexture) {
        setIcon(iconTexture);
        return self();
    }

    public SELF icon(ResourceLocation iconTexture, boolean stretchToFit, boolean allowUpscale) {
        setIcon(iconTexture, stretchToFit, allowUpscale);
        return self();
    }

    public SELF iconSizePx(int sizePx) {
        setIconSizePx(sizePx);
        return self();
    }

    public SELF contentGapPx(int gapPx) {
        setContentGapPx(gapPx);
        return self();
    }

    public SELF contentPaddingPx(int horizontalPx, int verticalPx) {
        setContentPaddingPx(horizontalPx, verticalPx);
        return self();
    }

    public SELF layout(IFizzyElementService.LtsxButtonContentLayout layout) {
        setLayout(layout);
        return self();
    }

    public SELF iconAlign(IFizzyElementService.LtsxIconVerticalAlign align) {
        setIconAlign(align);
        return self();
    }
}

final class RichButtonBuilderBridge
        extends AbstractRichButtonBuilder<FizzyButtonElement.Builder, IFizzyElementService.LtsxRichButtonBuilder>
        implements IFizzyElementService.LtsxRichButtonBuilder {
    RichButtonBuilderBridge(FizzyButtonElement.Builder delegate) {
        super(delegate);
    }

    @Override
    protected IFizzyElementService.LtsxRichButtonBuilder self() {
        return this;
    }

    @Override
    protected void setTooltip(Tooltip tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setTooltip(Component tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setTooltip(FizzyTooltipElement tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setPressSound(SoundEvent soundEvent) {
        delegate.pressSound(soundEvent);
    }

    @Override
    protected void setNarration(Component narration) {
        delegate.narration(narration);
    }

    @Override
    protected void setText(Component text) {
        delegate.text(text);
    }

    @Override
    protected void setTextConfig(Consumer<FizzyComponentElement.Builder> textConfigurer) {
        delegate.textConfig(textConfigurer);
    }

    @Override
    protected void setIcon(ResourceLocation iconTexture) {
        delegate.icon(iconTexture);
    }

    @Override
    protected void setIcon(ResourceLocation iconTexture, boolean stretchToFit, boolean allowUpscale) {
        delegate.icon(iconTexture, stretchToFit, allowUpscale);
    }

    @Override
    protected void setIconSizePx(int sizePx) {
        delegate.iconSizePx(sizePx);
    }

    @Override
    protected void setContentGapPx(int gapPx) {
        delegate.contentGapPx(gapPx);
    }

    @Override
    protected void setContentPaddingPx(int horizontalPx, int verticalPx) {
        delegate.contentPaddingPx(horizontalPx, verticalPx);
    }

    @Override
    protected void setLayout(IFizzyElementService.LtsxButtonContentLayout layout) {
        delegate.layout(FizzyButtonElement.ContentLayout.valueOf(layout.name()));
    }

    @Override
    protected void setIconAlign(IFizzyElementService.LtsxIconVerticalAlign align) {
        delegate.iconAlign(FizzyButtonElement.IconVerticalAlign.valueOf(align.name()));
    }
}

final class VanillaButtonBuilderBridge
        extends AbstractRichButtonBuilder<VanillaLikeButtonElement.Builder, IFizzyElementService.LtsxVanillaButtonBuilder>
        implements IFizzyElementService.LtsxVanillaButtonBuilder {
    VanillaButtonBuilderBridge(VanillaLikeButtonElement.Builder delegate) {
        super(delegate);
    }

    @Override
    protected IFizzyElementService.LtsxVanillaButtonBuilder self() {
        return this;
    }

    @Override
    protected void setTooltip(Tooltip tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setTooltip(Component tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setTooltip(FizzyTooltipElement tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setPressSound(SoundEvent soundEvent) {
        delegate.pressSound(soundEvent);
    }

    @Override
    protected void setNarration(Component narration) {
        delegate.narration(narration);
    }

    @Override
    protected void setText(Component text) {
        delegate.text(text);
    }

    @Override
    protected void setTextConfig(Consumer<FizzyComponentElement.Builder> textConfigurer) {
        delegate.textConfig(textConfigurer);
    }

    @Override
    protected void setIcon(ResourceLocation iconTexture) {
        delegate.icon(iconTexture);
    }

    @Override
    protected void setIcon(ResourceLocation iconTexture, boolean stretchToFit, boolean allowUpscale) {
        delegate.icon(iconTexture, stretchToFit, allowUpscale);
    }

    @Override
    protected void setIconSizePx(int sizePx) {
        delegate.iconSizePx(sizePx);
    }

    @Override
    protected void setContentGapPx(int gapPx) {
        delegate.contentGapPx(gapPx);
    }

    @Override
    protected void setContentPaddingPx(int horizontalPx, int verticalPx) {
        delegate.contentPaddingPx(horizontalPx, verticalPx);
    }

    @Override
    protected void setLayout(IFizzyElementService.LtsxButtonContentLayout layout) {
        delegate.layout(VanillaLikeButtonElement.ContentLayout.valueOf(layout.name()));
    }

    @Override
    protected void setIconAlign(IFizzyElementService.LtsxIconVerticalAlign align) {
        delegate.iconAlign(VanillaLikeButtonElement.IconVerticalAlign.valueOf(align.name()));
    }

    @Override
    public IFizzyElementService.LtsxVanillaButtonBuilder colorTheme(IFizzyElementService.LtsxVanillaButtonColorTheme theme) {
        delegate.colorTheme(FizzyElementService.mapVanillaColor(theme));
        return this;
    }
}

final class ColoredButtonBuilderBridge
        extends AbstractRichButtonBuilder<ColoredButtonElement.Builder, IFizzyElementService.LtsxColoredButtonBuilder>
        implements IFizzyElementService.LtsxColoredButtonBuilder {
    ColoredButtonBuilderBridge(ColoredButtonElement.Builder delegate) {
        super(delegate);
    }

    @Override
    protected IFizzyElementService.LtsxColoredButtonBuilder self() {
        return this;
    }

    @Override
    protected void setTooltip(Tooltip tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setTooltip(Component tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setTooltip(FizzyTooltipElement tooltip) {
        delegate.tooltip(tooltip);
    }

    @Override
    protected void setPressSound(SoundEvent soundEvent) {
        delegate.pressSound(soundEvent);
    }

    @Override
    protected void setNarration(Component narration) {
        delegate.narration(narration);
    }

    @Override
    protected void setText(Component text) {
        delegate.text(text);
    }

    @Override
    protected void setTextConfig(Consumer<FizzyComponentElement.Builder> textConfigurer) {
        delegate.textConfig(textConfigurer);
    }

    @Override
    protected void setIcon(ResourceLocation iconTexture) {
        delegate.icon(iconTexture);
    }

    @Override
    protected void setIcon(ResourceLocation iconTexture, boolean stretchToFit, boolean allowUpscale) {
        delegate.icon(iconTexture, stretchToFit, allowUpscale);
    }

    @Override
    protected void setIconSizePx(int sizePx) {
        delegate.iconSizePx(sizePx);
    }

    @Override
    protected void setContentGapPx(int gapPx) {
        delegate.contentGapPx(gapPx);
    }

    @Override
    protected void setContentPaddingPx(int horizontalPx, int verticalPx) {
        delegate.contentPaddingPx(horizontalPx, verticalPx);
    }

    @Override
    protected void setLayout(IFizzyElementService.LtsxButtonContentLayout layout) {
        delegate.layout(ColoredButtonElement.ContentLayout.valueOf(layout.name()));
    }

    @Override
    protected void setIconAlign(IFizzyElementService.LtsxIconVerticalAlign align) {
        delegate.iconAlign(ColoredButtonElement.IconVerticalAlign.valueOf(align.name()));
    }

    @Override
    public IFizzyElementService.LtsxColoredButtonBuilder color(IFizzyElementService.LtsxColoredButtonColor color) {
        delegate.color(FizzyElementService.mapColoredColor(color));
        return this;
    }
}

final class WidgetButtonBuilderBridge implements IFizzyElementService.LtsxWidgetButtonBuilder {
    private final WidgetButtonElement.Builder delegate;

    WidgetButtonBuilderBridge(WidgetButtonElement.Builder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxWidgetButtonBuilder type(IFizzyElementService.LtsxWidgetType type) {
        delegate.type(FizzyElementService.mapWidgetType(type));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxWidgetButtonBuilder color(IFizzyElementService.LtsxWidgetColor color) {
        delegate.color(FizzyElementService.mapWidgetColor(color));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxWidgetButtonBuilder direction(IFizzyElementService.LtsxArrowDirection direction) {
        delegate.direction(FizzyElementService.mapArrowDirection(direction));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxWidgetButtonBuilder stretchToFit(boolean stretchToFit) {
        delegate.stretchToFit(stretchToFit);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxWidgetButtonBuilder tooltip(Tooltip tooltip) {
        delegate.tooltip(tooltip);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxWidgetButtonBuilder tooltip(Component tooltip) {
        delegate.tooltip(tooltip);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxWidgetButtonBuilder tooltip(IFizzyElementService.LtsxElement tooltipElement) {
        delegate.tooltip(FizzyElementService.unwrapTooltip(tooltipElement));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxWidgetButtonBuilder pressSound(SoundEvent soundEvent) {
        delegate.pressSound(soundEvent);
        return this;
    }
}

final class TransparentIconButtonBuilderBridge implements IFizzyElementService.LtsxIconButtonBuilder {
    private final TransparentButtonElement.Builder delegate;

    TransparentIconButtonBuilderBridge(TransparentButtonElement.Builder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder stretchToFit(boolean stretchToFit) {
        delegate.stretchToFit(stretchToFit);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder allowUpscale(boolean allowUpscale) {
        delegate.allowUpscale(allowUpscale);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder tooltip(Tooltip tooltip) {
        delegate.tooltip(tooltip);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder tooltip(Component tooltip) {
        delegate.tooltip(tooltip);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder tooltip(IFizzyElementService.LtsxElement tooltipElement) {
        delegate.tooltip(FizzyElementService.unwrapTooltip(tooltipElement));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder pressSound(SoundEvent soundEvent) {
        delegate.pressSound(soundEvent);
        return this;
    }
}

final class IconButtonBuilderBridge implements IFizzyElementService.LtsxIconButtonBuilder {
    private final IconButtonElement.Builder delegate;

    IconButtonBuilderBridge(IconButtonElement.Builder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder stretchToFit(boolean stretchToFit) {
        delegate.stretchToFit(stretchToFit);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder allowUpscale(boolean allowUpscale) {
        delegate.allowUpscale(allowUpscale);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder tooltip(Tooltip tooltip) {
        delegate.tooltip(tooltip);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder tooltip(Component tooltip) {
        delegate.tooltip(tooltip);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder tooltip(IFizzyElementService.LtsxElement tooltipElement) {
        delegate.tooltip(FizzyElementService.unwrapTooltip(tooltipElement));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxIconButtonBuilder pressSound(SoundEvent soundEvent) {
        delegate.pressSound(soundEvent);
        return this;
    }
}
