package link.botwmcs.core.service.fizzy.client.overlay;

import java.util.Objects;
import link.botwmcs.core.api.fizzier.overlay.IFizzyOverlayClientService;
import link.botwmcs.fizzy.client.overlay.CreateHudOverlay;
import link.botwmcs.fizzy.client.overlay.ModalManager;
import link.botwmcs.fizzy.client.overlay.NotificationManager;
import link.botwmcs.fizzy.client.overlay.OverlayManager;
import link.botwmcs.fizzy.ui.kernel.modal.ModalOverlay;
import link.botwmcs.fizzy.ui.kernel.modal.ModalSpec;
import link.botwmcs.fizzy.ui.kernel.notification.NotificationLevel;
import link.botwmcs.fizzy.ui.kernel.notification.NotificationOverlay;
import link.botwmcs.fizzy.ui.kernel.notification.NotificationSpec;
import net.minecraft.network.chat.Component;

public final class FizzyOverlayClientService implements IFizzyOverlayClientService {
    @Override
    public LtsxHudOverlayHandle createHudOverlay() {
        return new HudOverlayBridge(OverlayManager.create());
    }

    @Override
    public LtsxHudOverlayHandle createHudOverlay(ILtsxOverlayContent content) {
        return new HudOverlayBridge(OverlayManager.create(adaptContent(content)));
    }

    @Override
    public void removeHudOverlay(LtsxHudOverlayHandle overlayHandle) {
        OverlayManager.remove(((HudOverlayBridge) overlayHandle).delegate);
    }

    @Override
    public LtsxOverlayHandle showNotification(Component title, Component message) {
        return new NotificationOverlayBridge(NotificationManager.show(title, message));
    }

    @Override
    public LtsxOverlayHandle showNotification(LtsxNotificationSpec spec) {
        NotificationSpec.Builder builder = NotificationSpec.builder()
                .title(spec.title())
                .message(spec.message())
                .level(mapLevel(spec.level()))
                .durationTicks(spec.durationTicks())
                .anchor(mapAnchor(spec.anchor()));
        return new NotificationOverlayBridge(NotificationManager.show(builder.build()));
    }

    @Override
    public LtsxOverlayHandle showModal(Component title, Component message) {
        return new ModalOverlayBridge(ModalManager.show(title, message));
    }

    @Override
    public LtsxOverlayHandle showModal(LtsxModalSpec spec) {
        ModalSpec.Builder builder = ModalSpec.builder()
                .title(spec.title())
                .message(spec.message())
                .widthPx(spec.widthPx())
                .heightPx(spec.heightPx())
                .anchor(mapAnchor(spec.anchor()));
        return new ModalOverlayBridge(ModalManager.show(builder.build()));
    }

    @Override
    public void hideAll() {
        OverlayManager.hideAll();
        NotificationManager.hideAll();
        ModalManager.hideAll();
    }

    @Override
    public void clear() {
        OverlayManager.clear();
        NotificationManager.clear();
        ModalManager.clear();
    }

    @Override
    public void setLayout(int left, int top, int right, int bottom) {
        OverlayManager.setLayout(left, top, right, bottom);
        NotificationManager.setLayout(left, top, right, bottom);
        ModalManager.setLayout(left, top, right, bottom);
    }

    private static link.botwmcs.fizzy.api.IOverlayContent adaptContent(ILtsxOverlayContent content) {
        Objects.requireNonNull(content, "content");
        return new link.botwmcs.fizzy.api.IOverlayContent() {
            @Override
            public void renderBackLayer(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                content.renderBackLayer(guiGraphics, mouseX, mouseY, partialTick);
            }

            @Override
            public void renderMainLayer(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                content.renderMainLayer(guiGraphics, mouseX, mouseY, partialTick);
            }

            @Override
            public void renderFrontLayer(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                content.renderFrontLayer(guiGraphics, mouseX, mouseY, partialTick);
            }

            @Override
            public void tick() {
                content.tick();
            }

            @Override
            public boolean isImportant() {
                return content.isImportant();
            }

            @Override
            public int preferredHeightPx() {
                return content.preferredHeightPx();
            }

            @Override
            public void onClose() {
                content.onClose();
            }

            @Override
            public void setExternalAlpha(float alpha) {
                content.setExternalAlpha(alpha);
            }
        };
    }

    private static link.botwmcs.fizzy.client.overlay.Anchor mapAnchor(LtsxAnchor anchor) {
        return switch (anchor) {
            case TOP_LEFT -> link.botwmcs.fizzy.client.overlay.Anchor.TOP_LEFT;
            case TOP_RIGHT -> link.botwmcs.fizzy.client.overlay.Anchor.TOP_RIGHT;
            case BOTTOM_LEFT -> link.botwmcs.fizzy.client.overlay.Anchor.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> link.botwmcs.fizzy.client.overlay.Anchor.BOTTOM_RIGHT;
        };
    }

    private static LtsxAnchor mapAnchor(link.botwmcs.fizzy.client.overlay.Anchor anchor) {
        return switch (anchor) {
            case TOP_LEFT -> LtsxAnchor.TOP_LEFT;
            case TOP_RIGHT -> LtsxAnchor.TOP_RIGHT;
            case BOTTOM_LEFT -> LtsxAnchor.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> LtsxAnchor.BOTTOM_RIGHT;
        };
    }

    private static link.botwmcs.fizzy.client.overlay.Transition mapTransition(LtsxTransition transition) {
        return switch (transition) {
            case CROSS_FADE -> link.botwmcs.fizzy.client.overlay.Transition.CROSS_FADE;
        };
    }

    private static NotificationLevel mapLevel(LtsxNotificationLevel level) {
        return switch (level) {
            case INFO -> NotificationLevel.INFO;
            case SUCCESS -> NotificationLevel.SUCCESS;
            case WARNING -> NotificationLevel.WARNING;
            case ERROR -> NotificationLevel.ERROR;
        };
    }

    private abstract static class BaseOverlayBridge implements LtsxOverlayHandle {
        @Override
        public LtsxAnchor anchor() {
            return mapAnchor(rawAnchor());
        }

        @Override
        public void setAnchor(LtsxAnchor anchor) {
            assignRawAnchor(mapAnchor(anchor));
        }

        protected abstract link.botwmcs.fizzy.client.overlay.Anchor rawAnchor();

        protected abstract void assignRawAnchor(link.botwmcs.fizzy.client.overlay.Anchor anchor);
    }

    private static final class HudOverlayBridge extends BaseOverlayBridge implements LtsxHudOverlayHandle {
        private final CreateHudOverlay delegate;

        private HudOverlayBridge(CreateHudOverlay delegate) {
            this.delegate = delegate;
        }

        @Override
        public LtsxHudOverlayHandle setScale(float scale) {
            delegate.setScale(scale);
            return this;
        }

        @Override
        public LtsxHudOverlayHandle setContent(ILtsxOverlayContent content) {
            delegate.setContent(adaptContent(content));
            return this;
        }

        @Override
        public LtsxHudOverlayHandle setContentAnimated(ILtsxOverlayContent content, LtsxTransition transition, double seconds) {
            delegate.setContentAnimated(adaptContent(content), mapTransition(transition), seconds);
            return this;
        }

        @Override
        public LtsxHudOverlayHandle setTitle(Component title) {
            delegate.setTitle(title);
            return this;
        }

        @Override
        public LtsxHudOverlayHandle setSlidingText(Component text) {
            delegate.setSlidingText(text);
            return this;
        }

        @Override
        public void show() {
            delegate.show();
        }

        @Override
        public void hide() {
            delegate.hide();
        }

        @Override
        public boolean isActive() {
            return delegate.isActive();
        }

        @Override
        public void dispose() {
            delegate.dispose();
        }

        @Override
        public int widthPx() {
            return delegate.getWidthPx();
        }

        @Override
        public int heightPx() {
            return delegate.getHeightPx();
        }

        @Override
        public void setTargetPos(int x, int y) {
            delegate.setTargetPos(x, y);
        }

        @Override
        protected link.botwmcs.fizzy.client.overlay.Anchor rawAnchor() {
            return delegate.getAnchor();
        }

        @Override
        protected void assignRawAnchor(link.botwmcs.fizzy.client.overlay.Anchor anchor) {
            delegate.setAnchor(anchor);
        }
    }

    private static final class NotificationOverlayBridge extends BaseOverlayBridge {
        private final NotificationOverlay delegate;

        private NotificationOverlayBridge(NotificationOverlay delegate) {
            this.delegate = delegate;
        }

        @Override
        public void hide() {
            delegate.hide();
        }

        @Override
        public boolean isActive() {
            return delegate.isActive();
        }

        @Override
        public void dispose() {
            delegate.dispose();
        }

        @Override
        public int widthPx() {
            return delegate.getWidthPx();
        }

        @Override
        public int heightPx() {
            return delegate.getHeightPx();
        }

        @Override
        public void setTargetPos(int x, int y) {
            delegate.setTargetPos(x, y);
        }

        @Override
        protected link.botwmcs.fizzy.client.overlay.Anchor rawAnchor() {
            return delegate.getAnchor();
        }

        @Override
        protected void assignRawAnchor(link.botwmcs.fizzy.client.overlay.Anchor anchor) {
            delegate.assignAnchor(anchor);
        }
    }

    private static final class ModalOverlayBridge extends BaseOverlayBridge {
        private final ModalOverlay delegate;

        private ModalOverlayBridge(ModalOverlay delegate) {
            this.delegate = delegate;
        }

        @Override
        public void hide() {
            delegate.hide();
        }

        @Override
        public boolean isActive() {
            return delegate.isActive();
        }

        @Override
        public void dispose() {
            delegate.dispose();
        }

        @Override
        public int widthPx() {
            return delegate.getWidthPx();
        }

        @Override
        public int heightPx() {
            return delegate.getHeightPx();
        }

        @Override
        public void setTargetPos(int x, int y) {
            delegate.setTargetPos(x, y);
        }

        @Override
        protected link.botwmcs.fizzy.client.overlay.Anchor rawAnchor() {
            return delegate.getAnchor();
        }

        @Override
        protected void assignRawAnchor(link.botwmcs.fizzy.client.overlay.Anchor anchor) {
            delegate.assignAnchor(anchor);
        }
    }
}
