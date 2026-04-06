package link.botwmcs.core.service.fizzy.client.element;

import java.util.Objects;
import java.util.function.Consumer;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.core.api.fizzier.text.IFizzyTextService;
import link.botwmcs.core.service.fizzy.client.bridge.FizzyClientBridges;
import link.botwmcs.fizzy.client.elements.ColoredAbstractButton;
import link.botwmcs.fizzy.client.elements.VanillaLikeAbstractButton;
import link.botwmcs.fizzy.client.elements.WidgetAbstractButton;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.animate.AnimatedElement;
import link.botwmcs.fizzy.ui.element.button.ColoredButtonElement;
import link.botwmcs.fizzy.ui.element.button.FizzyButtonElement;
import link.botwmcs.fizzy.ui.element.button.IconButtonElement;
import link.botwmcs.fizzy.ui.element.button.TransparentButtonElement;
import link.botwmcs.fizzy.ui.element.button.VanillaLikeButtonElement;
import link.botwmcs.fizzy.ui.element.button.WidgetButtonElement;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.element.component.FizzyTooltipElement;
import link.botwmcs.fizzy.ui.element.funstuff.slotstuff.SlotBlockerElement;
import link.botwmcs.fizzy.ui.element.funstuff.vector.ContextMenuElement;
import link.botwmcs.fizzy.ui.element.funstuff.vector.ProgressElement;
import link.botwmcs.fizzy.ui.element.funstuff.vector.SimpleDraggableElement;
import link.botwmcs.fizzy.ui.element.component.SimpleChartsElement;
import link.botwmcs.fizzy.ui.element.icon.IconElement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class FizzyElementService implements IFizzyElementService {
    @Override
    public LtsxElement fizzyButton(Runnable onPress, Consumer<LtsxRichButtonBuilder> configurer) {
        FizzyButtonElement.Builder builder = FizzyButtonElement.builder(button -> run(onPress));
        configure(configurer, new RichButtonBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement vanillaButton(Runnable onPress, Consumer<LtsxVanillaButtonBuilder> configurer) {
        VanillaLikeButtonElement.Builder builder = VanillaLikeButtonElement.builder(button -> run(onPress));
        configure(configurer, new VanillaButtonBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement coloredButton(Runnable onPress, Consumer<LtsxColoredButtonBuilder> configurer) {
        ColoredButtonElement.Builder builder = ColoredButtonElement.builder(button -> run(onPress));
        configure(configurer, new ColoredButtonBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement widgetButton(Component narration, Runnable onPress, Consumer<LtsxWidgetButtonBuilder> configurer) {
        WidgetButtonElement.Builder builder = WidgetButtonElement.builder(
                Objects.requireNonNull(narration, "narration"),
                button -> run(onPress)
        );
        configure(configurer, new WidgetButtonBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement transparentButton(Component narration, Runnable onPress, Consumer<LtsxIconButtonBuilder> configurer) {
        TransparentButtonElement.Builder builder = TransparentButtonElement.builder(
                Objects.requireNonNull(narration, "narration"),
                button -> run(onPress)
        );
        configure(configurer, new TransparentIconButtonBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement iconButton(
            Component narration,
            ResourceLocation iconTexture,
            Runnable onPress,
            Consumer<LtsxIconButtonBuilder> configurer
    ) {
        IconButtonElement.Builder builder = IconButtonElement.builder(
                Objects.requireNonNull(narration, "narration"),
                button -> run(onPress),
                Objects.requireNonNull(iconTexture, "iconTexture")
        );
        configure(configurer, new IconButtonBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement component(Consumer<LtsxComponentBuilder> configurer) {
        FizzyComponentElement.Builder builder = FizzyComponentElement.builder();
        configure(configurer, new ComponentBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement tooltip(Consumer<LtsxTooltipBuilder> configurer) {
        FizzyTooltipElement.Builder builder = FizzyTooltipElement.builder();
        configure(configurer, new TooltipBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement icon(ResourceLocation iconTexture) {
        return FizzyClientBridges.wrapElement(new IconElement(Objects.requireNonNull(iconTexture, "iconTexture")));
    }

    @Override
    public LtsxElement progress(Consumer<LtsxProgressBuilder> configurer) {
        ProgressElement.Builder builder = ProgressElement.builder();
        configure(configurer, new ProgressBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement contextMenu(Consumer<LtsxContextMenuBuilder> configurer) {
        ContextMenuElement.Builder builder = ContextMenuElement.builder();
        configure(configurer, new RootContextMenuBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement draggable(
            Consumer<LtsxDraggableBuilder> contentConfigurer,
            Consumer<LtsxDraggableSettingsBuilder> settingsConfigurer
    ) {
        SimpleDraggableElement.ContentBuilder contentBuilder = SimpleDraggableElement.contentBuilder();
        configure(contentConfigurer, new DraggableBuilderBridge(contentBuilder));
        SimpleDraggableElement.Builder builder = SimpleDraggableElement.builder(contentBuilder.build());
        configure(settingsConfigurer, new DraggableSettingsBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    @Override
    public LtsxElement simpleDraggable(
            Consumer<LtsxDraggableBuilder> contentConfigurer,
            Consumer<LtsxDraggableSettingsBuilder> settingsConfigurer
    ) {
        return draggable(contentConfigurer, settingsConfigurer);
    }

    @Override
    public LtsxElement simpleCharts(Consumer<LtsxChartsBuilder> contentConfigurer) {
        SimpleChartsElement.ContentBuilder contentBuilder = SimpleChartsElement.contentBuilder();
        configure(contentConfigurer, new ChartsBuilderBridge(contentBuilder));
        return FizzyClientBridges.wrapElement(SimpleChartsElement.builder(contentBuilder.build()).build());
    }

    @Override
    public LtsxElement slotBlocker(boolean open) {
        return FizzyClientBridges.wrapElement(new SlotBlockerElement(open));
    }

    @Override
    public LtsxElement animate(LtsxElement element, Consumer<LtsxAnimationBuilder> configurer) {
        AnimatedElement.Builder builder = AnimatedElement.builder(unwrapElement(element));
        configure(configurer, new AnimationBuilderBridge(builder));
        return FizzyClientBridges.wrapElement(builder.build());
    }

    static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    static <T> void configure(Consumer<T> configurer, T target) {
        if (configurer != null) {
            configurer.accept(target);
        }
    }

    static ElementPainter unwrapElement(LtsxElement element) {
        return FizzyClientBridges.unwrapElement(Objects.requireNonNull(element, "element"));
    }

    static ElementPainter[] unwrapElements(LtsxElement... elements) {
        if (elements == null || elements.length == 0) {
            return new ElementPainter[0];
        }
        ElementPainter[] out = new ElementPainter[elements.length];
        for (int i = 0; i < elements.length; i++) {
            out[i] = unwrapElement(elements[i]);
        }
        return out;
    }

    static FizzyTooltipElement unwrapTooltip(LtsxElement element) {
        ElementPainter painter = unwrapElement(element);
        if (painter instanceof FizzyTooltipElement tooltipElement) {
            return tooltipElement;
        }
        throw new IllegalArgumentException("Tooltip element must come from IFizzyElementService.tooltip(...).");
    }

    static TextRenderer.Align mapAlign(IFizzyTextService.LtsxTextAlign align) {
        return switch (Objects.requireNonNull(align, "align")) {
            case LEFT -> TextRenderer.Align.LEFT;
            case CENTER -> TextRenderer.Align.CENTER;
            case RIGHT -> TextRenderer.Align.RIGHT;
        };
    }

    static VanillaLikeAbstractButton.ColorTheme mapVanillaColor(LtsxVanillaButtonColorTheme theme) {
        return VanillaLikeAbstractButton.ColorTheme.valueOf(Objects.requireNonNull(theme, "theme").name());
    }

    static ColoredAbstractButton.Color mapColoredColor(LtsxColoredButtonColor color) {
        return ColoredAbstractButton.Color.valueOf(Objects.requireNonNull(color, "color").name());
    }

    static WidgetAbstractButton.WidgetType mapWidgetType(LtsxWidgetType type) {
        return WidgetAbstractButton.WidgetType.valueOf(Objects.requireNonNull(type, "type").name());
    }

    static WidgetAbstractButton.WidgetColor mapWidgetColor(LtsxWidgetColor color) {
        return WidgetAbstractButton.WidgetColor.valueOf(Objects.requireNonNull(color, "color").name());
    }

    static WidgetAbstractButton.ArrowDirection mapArrowDirection(LtsxArrowDirection direction) {
        return WidgetAbstractButton.ArrowDirection.valueOf(Objects.requireNonNull(direction, "direction").name());
    }

    static ProgressElement.Color mapProgressColor(LtsxProgressColor color) {
        return ProgressElement.Color.valueOf(Objects.requireNonNull(color, "color").name());
    }
}
