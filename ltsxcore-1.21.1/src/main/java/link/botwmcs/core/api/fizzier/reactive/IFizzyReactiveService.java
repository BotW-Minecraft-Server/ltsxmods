package link.botwmcs.core.api.fizzier.reactive;

import java.util.function.Consumer;
import java.util.function.Supplier;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;

public interface IFizzyReactiveService {
    LtsxStateKernel kernel();

    LtsxLayoutModifier modifier();

    IFizzyElementService.LtsxElement row(Consumer<LtsxLayoutContainerBuilder> builderConsumer);

    IFizzyElementService.LtsxElement row(
            LtsxLayoutModifier modifier,
            int gapPx,
            LtsxLayoutAlign align,
            Consumer<LtsxLayoutContainerBuilder> builderConsumer
    );

    IFizzyElementService.LtsxElement column(Consumer<LtsxLayoutContainerBuilder> builderConsumer);

    IFizzyElementService.LtsxElement column(
            LtsxLayoutModifier modifier,
            int gapPx,
            LtsxLayoutAlign align,
            Consumer<LtsxLayoutContainerBuilder> builderConsumer
    );

    interface LtsxStateKernel {
        LtsxScope newScope();

        <T> LtsxMutableSignal<T> mutableSignal(T initialValue);

        <T> LtsxSignal<T> computedSignal(Supplier<? extends T> supplier);

        <T> LtsxSignal<T> computedSignal(LtsxScope scope, Supplier<? extends T> supplier);

        LtsxEffectHandle effect(Runnable effect);

        LtsxEffectHandle effect(LtsxScope scope, Runnable effect);

        void batch(Runnable runnable);

        void flush();
    }

    interface LtsxSignal<T> {
        T get();
    }

    interface LtsxMutableSignal<T> extends LtsxSignal<T> {
        void set(T value);
    }

    interface LtsxEffectHandle extends AutoCloseable {
        boolean isDisposed();

        @Override
        void close();
    }

    interface LtsxScope extends AutoCloseable {
        boolean isClosed();

        @Override
        void close();
    }

    interface LtsxLayoutModifier {
        LtsxLayoutModifier widthPx(int widthPx);

        LtsxLayoutModifier widthPercent(float widthPercent);

        LtsxLayoutModifier fillWidth();

        LtsxLayoutModifier autoWidth();

        LtsxLayoutModifier heightPx(int heightPx);

        LtsxLayoutModifier heightPercent(float heightPercent);

        LtsxLayoutModifier fillHeight();

        LtsxLayoutModifier autoHeight();

        LtsxLayoutModifier sizePx(int widthPx, int heightPx);

        LtsxLayoutModifier minSizePx(int minWidthPx, int minHeightPx);

        LtsxLayoutModifier grow(float growFactor);

        LtsxLayoutModifier visibleWhen(LtsxSignal<Boolean> visibleSignal);
    }

    interface LtsxLayoutContainerBuilder {
        LtsxLayoutContainerBuilder element(IFizzyElementService.LtsxElement element);

        LtsxLayoutContainerBuilder element(IFizzyElementService.LtsxElement element, LtsxLayoutModifier modifier);

        LtsxLayoutContainerBuilder spacer();

        LtsxLayoutContainerBuilder spacer(LtsxLayoutModifier modifier);

        LtsxLayoutContainerBuilder row(Consumer<LtsxLayoutContainerBuilder> builderConsumer);

        LtsxLayoutContainerBuilder row(
                LtsxLayoutModifier modifier,
                int gapPx,
                LtsxLayoutAlign align,
                Consumer<LtsxLayoutContainerBuilder> builderConsumer
        );

        LtsxLayoutContainerBuilder column(Consumer<LtsxLayoutContainerBuilder> builderConsumer);

        LtsxLayoutContainerBuilder column(
                LtsxLayoutModifier modifier,
                int gapPx,
                LtsxLayoutAlign align,
                Consumer<LtsxLayoutContainerBuilder> builderConsumer
        );
    }

    enum LtsxLayoutAlign {
        START,
        CENTER,
        END,
        STRETCH
    }
}
