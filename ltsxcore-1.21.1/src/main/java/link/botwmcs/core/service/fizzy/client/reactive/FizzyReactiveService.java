package link.botwmcs.core.service.fizzy.client.reactive;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.core.api.fizzier.reactive.IFizzyReactiveService;
import link.botwmcs.core.service.fizzy.client.bridge.FizzyClientBridges;
import link.botwmcs.fizzy.ui.kernel.layout.LayoutAlign;
import link.botwmcs.fizzy.ui.kernel.layout.LayoutDsl;
import link.botwmcs.fizzy.ui.kernel.layout.LayoutModifier;
import link.botwmcs.fizzy.ui.kernel.state.EffectHandle;
import link.botwmcs.fizzy.ui.kernel.state.MutableSignal;
import link.botwmcs.fizzy.ui.kernel.state.Scope;
import link.botwmcs.fizzy.ui.kernel.state.Signal;
import link.botwmcs.fizzy.ui.kernel.state.StateKernel;

public final class FizzyReactiveService implements IFizzyReactiveService {
    @Override
    public LtsxStateKernel kernel() {
        return new StateKernelBridge(new StateKernel());
    }

    @Override
    public LtsxLayoutModifier modifier() {
        return new LayoutModifierBridge(LayoutDsl.modifier());
    }

    @Override
    public IFizzyElementService.LtsxElement row(Consumer<LtsxLayoutContainerBuilder> builderConsumer) {
        return FizzyClientBridges.wrapElement(LayoutDsl.painter(LayoutDsl.row(adaptConsumer(builderConsumer))));
    }

    @Override
    public IFizzyElementService.LtsxElement row(
            LtsxLayoutModifier modifier,
            int gapPx,
            LtsxLayoutAlign align,
            Consumer<LtsxLayoutContainerBuilder> builderConsumer
    ) {
        return FizzyClientBridges.wrapElement(LayoutDsl.painter(LayoutDsl.row(
                unwrapModifier(modifier),
                gapPx,
                mapAlign(align),
                adaptConsumer(builderConsumer)
        )));
    }

    @Override
    public IFizzyElementService.LtsxElement column(Consumer<LtsxLayoutContainerBuilder> builderConsumer) {
        return FizzyClientBridges.wrapElement(LayoutDsl.painter(LayoutDsl.column(adaptConsumer(builderConsumer))));
    }

    @Override
    public IFizzyElementService.LtsxElement column(
            LtsxLayoutModifier modifier,
            int gapPx,
            LtsxLayoutAlign align,
            Consumer<LtsxLayoutContainerBuilder> builderConsumer
    ) {
        return FizzyClientBridges.wrapElement(LayoutDsl.painter(LayoutDsl.column(
                unwrapModifier(modifier),
                gapPx,
                mapAlign(align),
                adaptConsumer(builderConsumer)
        )));
    }

    private static Consumer<LayoutDsl.ContainerBuilder> adaptConsumer(Consumer<LtsxLayoutContainerBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return raw -> consumer.accept(new ContainerBuilderBridge(raw));
    }

    private static LayoutAlign mapAlign(LtsxLayoutAlign align) {
        return switch (align) {
            case START -> LayoutAlign.START;
            case CENTER -> LayoutAlign.CENTER;
            case END -> LayoutAlign.END;
            case STRETCH -> LayoutAlign.STRETCH;
        };
    }

    private static LayoutModifier unwrapModifier(LtsxLayoutModifier modifier) {
        return ((LayoutModifierBridge) modifier).delegate;
    }

    private static final class StateKernelBridge implements LtsxStateKernel {
        private final StateKernel delegate;

        private StateKernelBridge(StateKernel delegate) {
            this.delegate = delegate;
        }

        @Override
        public LtsxScope newScope() {
            return new ScopeBridge(delegate.newScope());
        }

        @Override
        public <T> LtsxMutableSignal<T> mutableSignal(T initialValue) {
            return new MutableSignalBridge<>(delegate.mutableSignal(initialValue));
        }

        @Override
        public <T> LtsxSignal<T> computedSignal(Supplier<? extends T> supplier) {
            return new SignalBridge<>(delegate.computedSignal(supplier));
        }

        @Override
        public <T> LtsxSignal<T> computedSignal(LtsxScope scope, Supplier<? extends T> supplier) {
            return new SignalBridge<>(delegate.computedSignal(((ScopeBridge) scope).delegate, supplier));
        }

        @Override
        public LtsxEffectHandle effect(Runnable effect) {
            return new EffectHandleBridge(delegate.effect(effect));
        }

        @Override
        public LtsxEffectHandle effect(LtsxScope scope, Runnable effect) {
            return new EffectHandleBridge(delegate.effect(((ScopeBridge) scope).delegate, effect));
        }

        @Override
        public void batch(Runnable runnable) {
            delegate.batch(runnable);
        }

        @Override
        public void flush() {
            delegate.flush();
        }
    }

    private static final class LayoutModifierBridge implements LtsxLayoutModifier {
        private LayoutModifier delegate;

        private LayoutModifierBridge(LayoutModifier delegate) {
            this.delegate = delegate;
        }

        @Override
        public LtsxLayoutModifier widthPx(int widthPx) {
            delegate = delegate.widthPx(widthPx);
            return this;
        }

        @Override
        public LtsxLayoutModifier widthPercent(float widthPercent) {
            delegate = delegate.widthPercent(widthPercent);
            return this;
        }

        @Override
        public LtsxLayoutModifier fillWidth() {
            delegate = delegate.fillWidth();
            return this;
        }

        @Override
        public LtsxLayoutModifier autoWidth() {
            delegate = delegate.autoWidth();
            return this;
        }

        @Override
        public LtsxLayoutModifier heightPx(int heightPx) {
            delegate = delegate.heightPx(heightPx);
            return this;
        }

        @Override
        public LtsxLayoutModifier heightPercent(float heightPercent) {
            delegate = delegate.heightPercent(heightPercent);
            return this;
        }

        @Override
        public LtsxLayoutModifier fillHeight() {
            delegate = delegate.fillHeight();
            return this;
        }

        @Override
        public LtsxLayoutModifier autoHeight() {
            delegate = delegate.autoHeight();
            return this;
        }

        @Override
        public LtsxLayoutModifier sizePx(int widthPx, int heightPx) {
            delegate = delegate.sizePx(widthPx, heightPx);
            return this;
        }

        @Override
        public LtsxLayoutModifier minSizePx(int minWidthPx, int minHeightPx) {
            delegate = delegate.minSizePx(minWidthPx, minHeightPx);
            return this;
        }

        @Override
        public LtsxLayoutModifier grow(float growFactor) {
            delegate = delegate.grow(growFactor);
            return this;
        }

        @Override
        public LtsxLayoutModifier visibleWhen(LtsxSignal<Boolean> visibleSignal) {
            delegate = delegate.visibleWhen(((SignalBridge<Boolean>) visibleSignal).delegate);
            return this;
        }
    }

    private record ContainerBuilderBridge(LayoutDsl.ContainerBuilder delegate) implements LtsxLayoutContainerBuilder {
        @Override
        public LtsxLayoutContainerBuilder element(IFizzyElementService.LtsxElement element) {
            delegate.element(FizzyClientBridges.unwrapElement(element));
            return this;
        }

        @Override
        public LtsxLayoutContainerBuilder element(IFizzyElementService.LtsxElement element, LtsxLayoutModifier modifier) {
            delegate.element(FizzyClientBridges.unwrapElement(element), unwrapModifier(modifier));
            return this;
        }

        @Override
        public LtsxLayoutContainerBuilder spacer() {
            delegate.spacer();
            return this;
        }

        @Override
        public LtsxLayoutContainerBuilder spacer(LtsxLayoutModifier modifier) {
            delegate.spacer(unwrapModifier(modifier));
            return this;
        }

        @Override
        public LtsxLayoutContainerBuilder row(Consumer<LtsxLayoutContainerBuilder> builderConsumer) {
            delegate.row(adaptConsumer(builderConsumer));
            return this;
        }

        @Override
        public LtsxLayoutContainerBuilder row(
                LtsxLayoutModifier modifier,
                int gapPx,
                LtsxLayoutAlign align,
                Consumer<LtsxLayoutContainerBuilder> builderConsumer
        ) {
            delegate.row(unwrapModifier(modifier), gapPx, mapAlign(align), adaptConsumer(builderConsumer));
            return this;
        }

        @Override
        public LtsxLayoutContainerBuilder column(Consumer<LtsxLayoutContainerBuilder> builderConsumer) {
            delegate.column(adaptConsumer(builderConsumer));
            return this;
        }

        @Override
        public LtsxLayoutContainerBuilder column(
                LtsxLayoutModifier modifier,
                int gapPx,
                LtsxLayoutAlign align,
                Consumer<LtsxLayoutContainerBuilder> builderConsumer
        ) {
            delegate.column(unwrapModifier(modifier), gapPx, mapAlign(align), adaptConsumer(builderConsumer));
            return this;
        }
    }

    private static class SignalBridge<T> implements LtsxSignal<T> {
        protected final Signal<T> delegate;

        private SignalBridge(Signal<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T get() {
            return delegate.get();
        }
    }

    private static final class MutableSignalBridge<T> extends SignalBridge<T> implements LtsxMutableSignal<T> {
        private final MutableSignal<T> delegate;

        private MutableSignalBridge(MutableSignal<T> delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void set(T value) {
            delegate.set(value);
        }
    }

    private record EffectHandleBridge(EffectHandle delegate) implements LtsxEffectHandle {
        @Override
        public boolean isDisposed() {
            return delegate.isDisposed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private record ScopeBridge(Scope delegate) implements LtsxScope {
        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
