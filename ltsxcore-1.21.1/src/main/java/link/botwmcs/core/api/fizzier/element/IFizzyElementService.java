package link.botwmcs.core.api.fizzier.element;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public interface IFizzyElementService {
    LtsxElement fizzyButton(Runnable onPress, Consumer<LtsxRichButtonBuilder> configurer);

    LtsxElement vanillaButton(Runnable onPress, Consumer<LtsxVanillaButtonBuilder> configurer);

    LtsxElement coloredButton(Runnable onPress, Consumer<LtsxColoredButtonBuilder> configurer);

    LtsxElement widgetButton(Component narration, Runnable onPress, Consumer<LtsxWidgetButtonBuilder> configurer);

    LtsxElement transparentButton(Component narration, Runnable onPress, Consumer<LtsxIconButtonBuilder> configurer);

    LtsxElement iconButton(Component narration, ResourceLocation iconTexture, Runnable onPress, Consumer<LtsxIconButtonBuilder> configurer);

    LtsxElement component(Consumer<LtsxComponentBuilder> configurer);

    LtsxElement tooltip(Consumer<LtsxTooltipBuilder> configurer);

    LtsxElement icon(ResourceLocation iconTexture);

    LtsxElement progress(Consumer<LtsxProgressBuilder> configurer);

    LtsxElement contextMenu(Consumer<LtsxContextMenuBuilder> configurer);

    LtsxElement draggable(Consumer<LtsxDraggableBuilder> contentConfigurer, Consumer<LtsxDraggableSettingsBuilder> settingsConfigurer);

    LtsxElement slotBlocker(boolean open);

    LtsxElement animate(LtsxElement element, Consumer<LtsxAnimationBuilder> configurer);

    interface LtsxElement {
    }

    interface LtsxRichButtonBuilder {
        LtsxRichButtonBuilder tooltip(Tooltip tooltip);

        LtsxRichButtonBuilder tooltip(Component tooltip);

        LtsxRichButtonBuilder tooltip(LtsxElement tooltipElement);

        LtsxRichButtonBuilder pressSound(SoundEvent soundEvent);

        LtsxRichButtonBuilder narration(Component narration);

        LtsxRichButtonBuilder text(Component text);

        LtsxRichButtonBuilder text(Consumer<LtsxComponentBuilder> textConfigurer);

        LtsxRichButtonBuilder icon(ResourceLocation iconTexture);

        LtsxRichButtonBuilder icon(ResourceLocation iconTexture, boolean stretchToFit, boolean allowUpscale);

        LtsxRichButtonBuilder iconSizePx(int sizePx);

        LtsxRichButtonBuilder contentGapPx(int gapPx);

        LtsxRichButtonBuilder contentPaddingPx(int horizontalPx, int verticalPx);

        LtsxRichButtonBuilder layout(LtsxButtonContentLayout layout);

        LtsxRichButtonBuilder iconAlign(LtsxIconVerticalAlign align);
    }

    interface LtsxVanillaButtonBuilder extends LtsxRichButtonBuilder {
        LtsxVanillaButtonBuilder colorTheme(LtsxVanillaButtonColorTheme theme);
    }

    interface LtsxColoredButtonBuilder extends LtsxRichButtonBuilder {
        LtsxColoredButtonBuilder color(LtsxColoredButtonColor color);
    }

    interface LtsxWidgetButtonBuilder {
        LtsxWidgetButtonBuilder type(LtsxWidgetType type);

        LtsxWidgetButtonBuilder color(LtsxWidgetColor color);

        LtsxWidgetButtonBuilder direction(LtsxArrowDirection direction);

        LtsxWidgetButtonBuilder stretchToFit(boolean stretchToFit);

        LtsxWidgetButtonBuilder tooltip(Tooltip tooltip);

        LtsxWidgetButtonBuilder tooltip(Component tooltip);

        LtsxWidgetButtonBuilder tooltip(LtsxElement tooltipElement);

        LtsxWidgetButtonBuilder pressSound(SoundEvent soundEvent);
    }

    interface LtsxIconButtonBuilder {
        LtsxIconButtonBuilder stretchToFit(boolean stretchToFit);

        LtsxIconButtonBuilder allowUpscale(boolean allowUpscale);

        LtsxIconButtonBuilder tooltip(Tooltip tooltip);

        LtsxIconButtonBuilder tooltip(Component tooltip);

        LtsxIconButtonBuilder tooltip(LtsxElement tooltipElement);

        LtsxIconButtonBuilder pressSound(SoundEvent soundEvent);
    }

    interface LtsxComponentBuilder {
        LtsxComponentBuilder addText(Component text);

        LtsxComponentBuilder addText(String text);

        LtsxComponentBuilder addText(Component text, Consumer<link.botwmcs.core.api.fizzier.text.IFizzyTextService.LtsxTextRendererBuilder> styleConfigurer);

        LtsxComponentBuilder addTextLines(Component text);

        LtsxComponentBuilder wrap(boolean wrap);

        LtsxComponentBuilder autoEllipsis(boolean autoEllipsis);

        LtsxComponentBuilder centerEllipsis(boolean centerEllipsis);

        LtsxComponentBuilder align(link.botwmcs.core.api.fizzier.text.IFizzyTextService.LtsxTextAlign align);

        LtsxComponentBuilder lineSpacing(float lineSpacing);

        LtsxComponentBuilder textScale(float textScale);

        LtsxComponentBuilder letterSpacing(float letterSpacing);

        LtsxComponentBuilder color(int colorArgb);

        LtsxComponentBuilder shadow(boolean shadow);

        LtsxComponentBuilder clipToPad(boolean clipToPad);

        LtsxComponentBuilder allowOverflow(boolean allowOverflow);

        LtsxComponentBuilder bold(boolean bold);

        LtsxComponentBuilder underline(boolean underline);

        LtsxComponentBuilder strikethrough(boolean strikethrough);

        LtsxComponentBuilder gradient(int... colors);

        LtsxComponentBuilder rainbow();

        LtsxComponentBuilder rainbow(float speed);

        LtsxComponentBuilder floating(boolean enabled, float amplitude, float speed);

        LtsxComponentBuilder floatingPixelated(boolean pixelated);
    }

    interface LtsxTooltipBuilder extends LtsxComponentBuilder {
        LtsxTooltipBuilder maxWidthPx(int maxWidthPx);

        LtsxTooltipBuilder tooltipColors(int backgroundTop, int backgroundBottom, int borderTop, int borderBottom);
    }

    interface LtsxProgressBuilder {
        LtsxProgressBuilder progress(float progress);

        LtsxProgressBuilder color(LtsxProgressColor color);

        LtsxProgressBuilder barHeight(int barHeight);

        LtsxProgressBuilder autoNotches(boolean autoNotches);

        LtsxProgressBuilder minNotchSegmentWidthPx(int widthPx);

        LtsxProgressBuilder capWidthPx(int widthPx);

        LtsxProgressBuilder addNotch(int px);
    }

    interface LtsxContextMenuBuilder {
        LtsxContextMenuBuilder minMenuWidthPx(int widthPx);

        LtsxContextMenuBuilder baseRowHeightPx(int heightPx);

        LtsxContextMenuBuilder rowPaddingPx(int horizontalPx, int topPx, int bottomPx);

        LtsxContextMenuBuilder submenuArrowSpacePx(int widthPx);

        LtsxContextMenuBuilder animationDurationMs(int openMs, int closeMs);

        LtsxContextMenuBuilder item(Component label, Runnable action);

        LtsxContextMenuBuilder item(Component label, boolean enabled, Runnable action);

        LtsxContextMenuBuilder submenu(Component label, Consumer<LtsxContextMenuBuilder> submenuConfigurer);

        LtsxContextMenuBuilder submenu(Component label, boolean enabled, Consumer<LtsxContextMenuBuilder> submenuConfigurer);

        LtsxContextMenuBuilder separator();

        LtsxContextMenuBuilder element(LtsxElement element);

        LtsxContextMenuBuilder element(LtsxElement element, Runnable action);

        LtsxContextMenuBuilder element(LtsxElement element, boolean enabled, Runnable action);
    }

    interface LtsxDraggableBuilder {
        LtsxDraggableBuilder contentHeightPx(int heightPx);

        LtsxDraggableBuilder pad(int rowStart, int colStart, int rowEnd, int colEnd, Consumer<LtsxDraggablePadBuilder> configurer);

        LtsxDraggableBuilder padByPx(int x, int y, int width, int height, Consumer<LtsxDraggablePadBuilder> configurer);
    }

    interface LtsxDraggablePadBuilder {
        LtsxDraggablePadBuilder element(LtsxElement element);

        LtsxDraggablePadBuilder elements(LtsxElement... elements);

        LtsxDraggablePadBuilder inner();
    }

    interface LtsxDraggableSettingsBuilder {
        LtsxDraggableSettingsBuilder wheelStepPx(int px);

        LtsxDraggableSettingsBuilder scrollbarWidthPx(int px);

        LtsxDraggableSettingsBuilder scrollbarGapPx(int px);

        LtsxDraggableSettingsBuilder minThumbHeightPx(int px);
    }

    interface LtsxAnimationBuilder {
        LtsxAnimationBuilder fixedScale(float scale);

        LtsxAnimationBuilder fixedScale(float scaleX, float scaleY);

        LtsxAnimationBuilder pulseScale(float minScale, float maxScale, float speed);

        LtsxAnimationBuilder rotate(float degreesPerTick);

        LtsxAnimationBuilder tintFixed(int colorArgb);

        LtsxAnimationBuilder tintPulse(int colorA, int colorB, float speed);

        LtsxAnimationBuilder freeFall(float gravityPerTick);
    }

    enum LtsxButtonContentLayout {
        TEXT_LEFT_ICON_RIGHT,
        ICON_LEFT_TEXT_RIGHT
    }

    enum LtsxIconVerticalAlign {
        TOP,
        CENTER,
        BOTTOM
    }

    enum LtsxColoredButtonColor {
        BLUE,
        ORANGE,
        YELLOW,
        LIME,
        RED,
        PINK,
        CYAN
    }

    enum LtsxVanillaButtonColorTheme {
        GRAY,
        NOTICE_GREEN,
        GREEN,
        BLUE,
        RED
    }

    enum LtsxWidgetType {
        LONG_ARROW,
        SHORT_ARROW,
        TRIANGLE
    }

    enum LtsxWidgetColor {
        GRAY,
        WOOD,
        GREEN,
        YELLOW,
        CYAN,
        RED,
        ORANGE,
        VANILLA
    }

    enum LtsxArrowDirection {
        LEFT,
        RIGHT
    }

    enum LtsxProgressColor {
        BLUE,
        GREEN,
        PINK,
        PURPLE,
        RED,
        WHITE,
        YELLOW
    }
}
