package link.botwmcs.core.service.fizzy.client.gui;

import java.util.Objects;
import java.util.function.Consumer;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.core.api.fizzier.gui.IFizzyGuiService;
import link.botwmcs.core.service.fizzy.client.bridge.FizzyClientBridges;
import link.botwmcs.fizzy.ui.background.FizzyBg;
import link.botwmcs.fizzy.ui.background.SoildColorBg;
import link.botwmcs.fizzy.ui.behind.BlurBehind;
import link.botwmcs.fizzy.ui.behind.ImageBehind;
import link.botwmcs.fizzy.ui.behind.SoildColorBehind;
import link.botwmcs.fizzy.ui.behind.VanillaBehind;
import link.botwmcs.fizzy.ui.core.FizzyGuiBuilder;
import link.botwmcs.fizzy.ui.core.HostType;
import link.botwmcs.fizzy.ui.frame.FizzyFrame;
import link.botwmcs.fizzy.ui.frame.MotiveFrame;
import link.botwmcs.fizzy.ui.host.FizzyMenuScreenHost;
import link.botwmcs.fizzy.ui.host.FizzyScreenHost;
import link.botwmcs.fizzy.ui.pad.BasePadBuilder;
import link.botwmcs.fizzy.ui.pad.SlotPadBuilder;
import link.botwmcs.fizzy.ui.split.SplitType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class FizzyGuiService implements IFizzyGuiService {
    @Override
    public LtsxGuiBuilder builder() {
        return new GuiBuilderBridge(FizzyGuiBuilder.start());
    }

    @Override
    public LtsxFrame fizzyFrame(Component title) {
        return FizzyClientBridges.wrapFrame(new FizzyFrame(Objects.requireNonNull(title, "title")));
    }

    @Override
    public LtsxFrame fizzyFrame(Component title, boolean drawTitle) {
        return FizzyClientBridges.wrapFrame(new FizzyFrame(Objects.requireNonNull(title, "title"), drawTitle));
    }

    @Override
    public LtsxFrame motiveFrame(Component title) {
        return FizzyClientBridges.wrapFrame(new MotiveFrame(Objects.requireNonNull(title, "title")));
    }

    @Override
    public LtsxFrame motiveFrame(Component title, boolean drawTitle) {
        return FizzyClientBridges.wrapFrame(new MotiveFrame(Objects.requireNonNull(title, "title"), drawTitle));
    }

    @Override
    public LtsxBackground background(LtsxBackgroundType type) {
        return FizzyClientBridges.wrapBackground(new FizzyBg(link.botwmcs.fizzy.ui.background.BgType.valueOf(type.name())));
    }

    @Override
    public LtsxBackground solidBackground(int argb) {
        return FizzyClientBridges.wrapBackground(new SoildColorBg(argb));
    }

    @Override
    public LtsxBehind blurBehind() {
        return FizzyClientBridges.wrapBehind(new BlurBehind());
    }

    @Override
    public LtsxBehind vanillaBehind() {
        return FizzyClientBridges.wrapBehind(new VanillaBehind());
    }

    @Override
    public LtsxBehind solidBehind(int argb) {
        return FizzyClientBridges.wrapBehind(new SoildColorBehind(argb));
    }

    @Override
    public LtsxBehind imageBehind(ResourceLocation texture) {
        return FizzyClientBridges.wrapBehind(new ImageBehind(Objects.requireNonNull(texture, "texture")));
    }

    @Override
    public Screen createScreen(LtsxGui gui) {
        return new FizzyScreenHost(FizzyClientBridges.unwrapGui(Objects.requireNonNull(gui, "gui")));
    }

    @Override
    public <T extends AbstractContainerMenu> AbstractContainerScreen<T> createMenuScreen(
            T menu,
            Inventory inventory,
            Component title,
            LtsxGui gui
    ) {
        return new FizzyMenuScreenHost<>(
                Objects.requireNonNull(menu, "menu"),
                Objects.requireNonNull(inventory, "inventory"),
                Objects.requireNonNull(title, "title"),
                FizzyClientBridges.unwrapGui(Objects.requireNonNull(gui, "gui"))
        );
    }

    private static final class GuiBuilderBridge implements LtsxGuiBuilder {
        private final FizzyGuiBuilder delegate;

        private GuiBuilderBridge(FizzyGuiBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public LtsxGuiBuilder sizeSlots(int slots) {
            delegate.sizeSlots(slots);
            return this;
        }

        @Override
        public LtsxGuiBuilder sizeSlots(int columns, int rows) {
            delegate.sizeSlots(columns, rows);
            return this;
        }

        @Override
        public LtsxGuiBuilder host(LtsxGuiHostType hostType) {
            delegate.host(HostType.valueOf(Objects.requireNonNull(hostType, "hostType").name()));
            return this;
        }

        @Override
        public LtsxGuiBuilder frame(LtsxFrame frame) {
            delegate.frame(FizzyClientBridges.unwrapFrame(Objects.requireNonNull(frame, "frame")));
            return this;
        }

        @Override
        public LtsxGuiBuilder background(LtsxBackground background) {
            delegate.background(FizzyClientBridges.unwrapBackground(Objects.requireNonNull(background, "background")));
            return this;
        }

        @Override
        public LtsxGuiBuilder behind(LtsxBehind behind) {
            delegate.behind(FizzyClientBridges.unwrapBehind(Objects.requireNonNull(behind, "behind")));
            return this;
        }

        @Override
        public LtsxGuiBuilder below(IFizzyElementService.LtsxElement element) {
            delegate.below(FizzyClientBridges.unwrapElement(Objects.requireNonNull(element, "element")));
            return this;
        }

        @Override
        public LtsxGuiBuilder overrideSizePx(int width, int height) {
            delegate.overrideSizePx(width, height);
            return this;
        }

        @Override
        public LtsxGuiBuilder pad(int rowStart, int colStart, int rowEnd, int colEnd, Consumer<LtsxPadBuilder> configurer) {
            SlotPadBuilder padBuilder = delegate.pad(rowStart, colStart, rowEnd, colEnd);
            configurePad(padBuilder, padBuilder::inner, configurer);
            return this;
        }

        @Override
        public LtsxGuiBuilder padAuto(int rowStart, int colStart, int rowEnd, int colEnd, Consumer<LtsxPadBuilder> configurer) {
            configurePad(delegate.padAuto(rowStart, colStart, rowEnd, colEnd), null, configurer);
            return this;
        }

        @Override
        public LtsxGuiBuilder padByPx(int x, int y, int width, int height, Consumer<LtsxPadBuilder> configurer) {
            configurePad(delegate.padByPx(x, y, width, height), null, configurer);
            return this;
        }

        @Override
        public LtsxGuiBuilder padByFrame(Consumer<LtsxPadBuilder> configurer) {
            configurePad(delegate.padByFrame(), null, configurer);
            return this;
        }

        @Override
        public LtsxGuiBuilder split(int rowStart, int colStart, int rowEnd, int colEnd) {
            delegate.split(rowStart, colStart, rowEnd, colEnd);
            return this;
        }

        @Override
        public LtsxGuiBuilder splitByPx(int startPx, int endPx, int atPx, LtsxSplitType splitType) {
            delegate.splitByPx(startPx, endPx, atPx, SplitType.valueOf(Objects.requireNonNull(splitType, "splitType").name()));
            return this;
        }

        @Override
        public LtsxGui build() {
            return FizzyClientBridges.wrapGui(delegate.build());
        }

        private void configurePad(
                BasePadBuilder<?> padBuilder,
                Runnable innerAction,
                Consumer<LtsxPadBuilder> configurer
        ) {
            if (configurer != null) {
                configurer.accept(new PadBuilderBridge(padBuilder, innerAction));
            }
            padBuilder.done();
        }
    }

    private record PadBuilderBridge(BasePadBuilder<?> delegate, Runnable innerAction) implements LtsxPadBuilder {
        @Override
        public LtsxPadBuilder element(IFizzyElementService.LtsxElement element) {
            delegate.element(FizzyClientBridges.unwrapElement(Objects.requireNonNull(element, "element")));
            return this;
        }

        @Override
        public LtsxPadBuilder elements(IFizzyElementService.LtsxElement... elements) {
            if (elements == null || elements.length == 0) {
                return this;
            }
            var raw = new link.botwmcs.fizzy.ui.element.ElementPainter[elements.length];
            for (int i = 0; i < elements.length; i++) {
                raw[i] = FizzyClientBridges.unwrapElement(Objects.requireNonNull(elements[i], "elements[" + i + "]"));
            }
            delegate.elements(raw);
            return this;
        }

        @Override
        public LtsxPadBuilder inner() {
            if (innerAction != null) {
                innerAction.run();
            }
            return this;
        }
    }
}
