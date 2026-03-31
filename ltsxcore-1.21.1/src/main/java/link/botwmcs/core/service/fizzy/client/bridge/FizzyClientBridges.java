package link.botwmcs.core.service.fizzy.client.bridge;

import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.core.api.fizzier.gui.IFizzyGuiService;
import link.botwmcs.fizzy.ui.background.BgPainter;
import link.botwmcs.fizzy.ui.behind.BehindPainter;
import link.botwmcs.fizzy.ui.core.FizzyGui;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.frame.FramePainter;

public final class FizzyClientBridges {
    private FizzyClientBridges() {
    }

    public static IFizzyElementService.LtsxElement wrapElement(ElementPainter delegate) {
        return new ElementBridge(delegate);
    }

    public static ElementPainter unwrapElement(IFizzyElementService.LtsxElement element) {
        return ((ElementBridge) element).delegate;
    }

    public static IFizzyGuiService.LtsxGui wrapGui(FizzyGui delegate) {
        return new GuiBridge(delegate);
    }

    public static FizzyGui unwrapGui(IFizzyGuiService.LtsxGui gui) {
        return ((GuiBridge) gui).delegate;
    }

    public static IFizzyGuiService.LtsxFrame wrapFrame(FramePainter delegate) {
        return new FrameBridge(delegate);
    }

    public static FramePainter unwrapFrame(IFizzyGuiService.LtsxFrame frame) {
        return ((FrameBridge) frame).delegate;
    }

    public static IFizzyGuiService.LtsxBackground wrapBackground(BgPainter delegate) {
        return new BackgroundBridge(delegate);
    }

    public static BgPainter unwrapBackground(IFizzyGuiService.LtsxBackground background) {
        return ((BackgroundBridge) background).delegate;
    }

    public static IFizzyGuiService.LtsxBehind wrapBehind(BehindPainter delegate) {
        return new BehindBridge(delegate);
    }

    public static BehindPainter unwrapBehind(IFizzyGuiService.LtsxBehind behind) {
        return ((BehindBridge) behind).delegate;
    }

    private record ElementBridge(ElementPainter delegate) implements IFizzyElementService.LtsxElement {
    }

    private record GuiBridge(FizzyGui delegate) implements IFizzyGuiService.LtsxGui {
        @Override
        public int widthPx() {
            return delegate.widthPx();
        }

        @Override
        public int heightPx() {
            return delegate.heightPx();
        }
    }

    private record FrameBridge(FramePainter delegate) implements IFizzyGuiService.LtsxFrame {
    }

    private record BackgroundBridge(BgPainter delegate) implements IFizzyGuiService.LtsxBackground {
    }

    private record BehindBridge(BehindPainter delegate) implements IFizzyGuiService.LtsxBehind {
    }
}
