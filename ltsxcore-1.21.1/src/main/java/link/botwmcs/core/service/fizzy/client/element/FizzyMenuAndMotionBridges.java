package link.botwmcs.core.service.fizzy.client.element;

import java.util.function.Consumer;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.animate.AnimatedElement;
import link.botwmcs.fizzy.ui.element.animate.vector.FreeFallAnimation;
import link.botwmcs.fizzy.ui.element.animate.vector.RotateAnimation;
import link.botwmcs.fizzy.ui.element.animate.vector.ScaleAnimation;
import link.botwmcs.fizzy.ui.element.animate.vector.TintAnimation;
import link.botwmcs.fizzy.ui.element.component.SimpleChartsElement;
import link.botwmcs.fizzy.ui.element.funstuff.vector.ContextMenuElement;
import link.botwmcs.fizzy.ui.element.funstuff.vector.SimpleDraggableElement;
import net.minecraft.network.chat.Component;

interface MenuOps {
    void item(Component label, Runnable action);

    void item(Component label, boolean enabled, Runnable action);

    void submenu(Component label, Consumer<ContextMenuElement.MenuBuilder> submenuConfigurer);

    void submenu(Component label, boolean enabled, Consumer<ContextMenuElement.MenuBuilder> submenuConfigurer);

    void separator();

    void element(ElementPainter element);

    void element(ElementPainter element, Runnable action);

    void element(ElementPainter element, boolean enabled, Runnable action);
}

abstract class AbstractContextMenuBuilder<SELF extends IFizzyElementService.LtsxContextMenuBuilder>
        implements IFizzyElementService.LtsxContextMenuBuilder {
    private final MenuOps menuOps;

    protected AbstractContextMenuBuilder(MenuOps menuOps) {
        this.menuOps = menuOps;
    }

    protected abstract SELF self();

    @Override
    public SELF item(Component label, Runnable action) {
        menuOps.item(label, action);
        return self();
    }

    @Override
    public SELF item(Component label, boolean enabled, Runnable action) {
        menuOps.item(label, enabled, action);
        return self();
    }

    @Override
    public SELF submenu(Component label, Consumer<IFizzyElementService.LtsxContextMenuBuilder> submenuConfigurer) {
        menuOps.submenu(label, raw -> FizzyElementService.configure(submenuConfigurer, new NestedContextMenuBuilderBridge(raw)));
        return self();
    }

    @Override
    public SELF submenu(Component label, boolean enabled, Consumer<IFizzyElementService.LtsxContextMenuBuilder> submenuConfigurer) {
        menuOps.submenu(label, enabled, raw -> FizzyElementService.configure(submenuConfigurer, new NestedContextMenuBuilderBridge(raw)));
        return self();
    }

    @Override
    public SELF separator() {
        menuOps.separator();
        return self();
    }

    @Override
    public SELF element(IFizzyElementService.LtsxElement element) {
        menuOps.element(FizzyElementService.unwrapElement(element));
        return self();
    }

    @Override
    public SELF element(IFizzyElementService.LtsxElement element, Runnable action) {
        menuOps.element(FizzyElementService.unwrapElement(element), action);
        return self();
    }

    @Override
    public SELF element(IFizzyElementService.LtsxElement element, boolean enabled, Runnable action) {
        menuOps.element(FizzyElementService.unwrapElement(element), enabled, action);
        return self();
    }

    @Override
    public SELF minMenuWidthPx(int widthPx) {
        return self();
    }

    @Override
    public SELF baseRowHeightPx(int heightPx) {
        return self();
    }

    @Override
    public SELF rowPaddingPx(int horizontalPx, int topPx, int bottomPx) {
        return self();
    }

    @Override
    public SELF submenuArrowSpacePx(int widthPx) {
        return self();
    }

    @Override
    public SELF animationDurationMs(int openMs, int closeMs) {
        return self();
    }
}

final class RootContextMenuBuilderBridge extends AbstractContextMenuBuilder<IFizzyElementService.LtsxContextMenuBuilder> {
    private final ContextMenuElement.Builder delegate;

    RootContextMenuBuilderBridge(ContextMenuElement.Builder delegate) {
        super(new MenuOps() {
            @Override
            public void item(Component label, Runnable action) {
                delegate.item(label, action);
            }

            @Override
            public void item(Component label, boolean enabled, Runnable action) {
                delegate.item(label, enabled, action);
            }

            @Override
            public void submenu(Component label, Consumer<ContextMenuElement.MenuBuilder> submenuConfigurer) {
                delegate.submenu(label, submenuConfigurer);
            }

            @Override
            public void submenu(Component label, boolean enabled, Consumer<ContextMenuElement.MenuBuilder> submenuConfigurer) {
                delegate.submenu(label, enabled, submenuConfigurer);
            }

            @Override
            public void separator() {
                delegate.separator();
            }

            @Override
            public void element(ElementPainter element) {
                delegate.element(element);
            }

            @Override
            public void element(ElementPainter element, Runnable action) {
                delegate.element(element, action);
            }

            @Override
            public void element(ElementPainter element, boolean enabled, Runnable action) {
                delegate.element(element, enabled, action);
            }
        });
        this.delegate = delegate;
    }

    @Override
    protected IFizzyElementService.LtsxContextMenuBuilder self() {
        return this;
    }

    @Override
    public IFizzyElementService.LtsxContextMenuBuilder minMenuWidthPx(int widthPx) {
        delegate.minMenuWidthPx(widthPx);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxContextMenuBuilder baseRowHeightPx(int heightPx) {
        delegate.baseRowHeightPx(heightPx);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxContextMenuBuilder rowPaddingPx(int horizontalPx, int topPx, int bottomPx) {
        delegate.rowPaddingPx(horizontalPx, topPx, bottomPx);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxContextMenuBuilder submenuArrowSpacePx(int widthPx) {
        delegate.submenuArrowSpacePx(widthPx);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxContextMenuBuilder animationDurationMs(int openMs, int closeMs) {
        delegate.animationDurationMs(openMs, closeMs);
        return this;
    }
}

final class NestedContextMenuBuilderBridge extends AbstractContextMenuBuilder<IFizzyElementService.LtsxContextMenuBuilder> {
    NestedContextMenuBuilderBridge(ContextMenuElement.MenuBuilder delegate) {
        super(new MenuOps() {
            @Override
            public void item(Component label, Runnable action) {
                delegate.item(label, action);
            }

            @Override
            public void item(Component label, boolean enabled, Runnable action) {
                delegate.item(label, enabled, action);
            }

            @Override
            public void submenu(Component label, Consumer<ContextMenuElement.MenuBuilder> submenuConfigurer) {
                delegate.submenu(label, submenuConfigurer);
            }

            @Override
            public void submenu(Component label, boolean enabled, Consumer<ContextMenuElement.MenuBuilder> submenuConfigurer) {
                delegate.submenu(label, enabled, submenuConfigurer);
            }

            @Override
            public void separator() {
                delegate.separator();
            }

            @Override
            public void element(ElementPainter element) {
                delegate.element(element);
            }

            @Override
            public void element(ElementPainter element, Runnable action) {
                delegate.element(element, action);
            }

            @Override
            public void element(ElementPainter element, boolean enabled, Runnable action) {
                delegate.element(element, enabled, action);
            }
        });
    }

    @Override
    protected IFizzyElementService.LtsxContextMenuBuilder self() {
        return this;
    }
}

final class DraggableBuilderBridge implements IFizzyElementService.LtsxDraggableBuilder {
    private final SimpleDraggableElement.ContentBuilder delegate;

    DraggableBuilderBridge(SimpleDraggableElement.ContentBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxDraggableBuilder contentHeightPx(int heightPx) {
        delegate.contentHeightPx(heightPx);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxDraggableBuilder pad(
            int rowStart,
            int colStart,
            int rowEnd,
            int colEnd,
            Consumer<IFizzyElementService.LtsxDraggablePadBuilder> configurer
    ) {
        SimpleDraggableElement.ContentPadBuilder builder = delegate.pad(rowStart, colStart, rowEnd, colEnd);
        FizzyElementService.configure(configurer, new DraggablePadBuilderBridge(builder));
        builder.done();
        return this;
    }

    @Override
    public IFizzyElementService.LtsxDraggableBuilder padByPx(
            int x,
            int y,
            int width,
            int height,
            Consumer<IFizzyElementService.LtsxDraggablePadBuilder> configurer
    ) {
        SimpleDraggableElement.ContentPadBuilder builder = delegate.padByPx(x, y, width, height);
        FizzyElementService.configure(configurer, new DraggablePadBuilderBridge(builder));
        builder.done();
        return this;
    }
}

final class DraggablePadBuilderBridge implements IFizzyElementService.LtsxDraggablePadBuilder {
    private final SimpleDraggableElement.ContentPadBuilder delegate;

    DraggablePadBuilderBridge(SimpleDraggableElement.ContentPadBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxDraggablePadBuilder element(IFizzyElementService.LtsxElement element) {
        delegate.element(FizzyElementService.unwrapElement(element));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxDraggablePadBuilder elements(IFizzyElementService.LtsxElement... elements) {
        delegate.elements(FizzyElementService.unwrapElements(elements));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxDraggablePadBuilder inner() {
        delegate.inner();
        return this;
    }
}

final class DraggableSettingsBuilderBridge implements IFizzyElementService.LtsxDraggableSettingsBuilder {
    private final SimpleDraggableElement.Builder delegate;

    DraggableSettingsBuilderBridge(SimpleDraggableElement.Builder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxDraggableSettingsBuilder wheelStepPx(int px) {
        delegate.wheelStepPx(px);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxDraggableSettingsBuilder scrollbarWidthPx(int px) {
        delegate.scrollbarWidthPx(px);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxDraggableSettingsBuilder scrollbarGapPx(int px) {
        delegate.scrollbarGapPx(px);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxDraggableSettingsBuilder minThumbHeightPx(int px) {
        delegate.minThumbHeightPx(px);
        return this;
    }
}

final class ChartsBuilderBridge implements IFizzyElementService.LtsxChartsBuilder {
    private final SimpleChartsElement.ContentBuilder delegate;

    ChartsBuilderBridge(SimpleChartsElement.ContentBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxChartsBuilder grid(int rows, int columns) {
        delegate.grid(rows, columns);
        return this;
    }

    @Override
    public IFizzyElementService.LtsxChartsBuilder cell(
            int row,
            int column,
            Consumer<IFizzyElementService.LtsxChartsCellBuilder> configurer
    ) {
        SimpleChartsElement.CellBuilder builder = delegate.cell(row, column);
        FizzyElementService.configure(configurer, new ChartsCellBuilderBridge(builder));
        builder.done();
        return this;
    }

    @Override
    public IFizzyElementService.LtsxChartsBuilder cell(
            int rowStart,
            int colStart,
            int rowEnd,
            int colEnd,
            Consumer<IFizzyElementService.LtsxChartsCellBuilder> configurer
    ) {
        SimpleChartsElement.CellBuilder builder = delegate.cell(rowStart, colStart, rowEnd, colEnd);
        FizzyElementService.configure(configurer, new ChartsCellBuilderBridge(builder));
        builder.done();
        return this;
    }
}

final class ChartsCellBuilderBridge implements IFizzyElementService.LtsxChartsCellBuilder {
    private final SimpleChartsElement.CellBuilder delegate;

    ChartsCellBuilderBridge(SimpleChartsElement.CellBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxChartsCellBuilder element(IFizzyElementService.LtsxElement element) {
        delegate.element(FizzyElementService.unwrapElement(element));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxChartsCellBuilder elements(IFizzyElementService.LtsxElement... elements) {
        delegate.elements(FizzyElementService.unwrapElements(elements));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxChartsCellBuilder inner() {
        delegate.inner();
        return this;
    }
}

final class AnimationBuilderBridge implements IFizzyElementService.LtsxAnimationBuilder {
    private final AnimatedElement.Builder delegate;

    AnimationBuilderBridge(AnimatedElement.Builder delegate) {
        this.delegate = delegate;
    }

    @Override
    public IFizzyElementService.LtsxAnimationBuilder fixedScale(float scale) {
        delegate.add(ScaleAnimation.fixed(scale));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxAnimationBuilder fixedScale(float scaleX, float scaleY) {
        delegate.add(ScaleAnimation.fixed(scaleX, scaleY));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxAnimationBuilder pulseScale(float minScale, float maxScale, float speed) {
        delegate.add(ScaleAnimation.pulse(minScale, maxScale, speed));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxAnimationBuilder rotate(float degreesPerTick) {
        delegate.add(new RotateAnimation(degreesPerTick));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxAnimationBuilder tintFixed(int colorArgb) {
        delegate.add(TintAnimation.fixed(colorArgb));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxAnimationBuilder tintPulse(int colorA, int colorB, float speed) {
        delegate.add(TintAnimation.pulse(colorA, colorB, speed));
        return this;
    }

    @Override
    public IFizzyElementService.LtsxAnimationBuilder freeFall(float gravityPerTick) {
        delegate.add(new FreeFallAnimation(gravityPerTick));
        return this;
    }
}
