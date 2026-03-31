package link.botwmcs.core.api.fizzier.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface IFizzyOverlayClientService {
    LtsxHudOverlayHandle createHudOverlay();

    LtsxHudOverlayHandle createHudOverlay(ILtsxOverlayContent content);

    void removeHudOverlay(LtsxHudOverlayHandle overlayHandle);

    LtsxOverlayHandle showNotification(Component title, Component message);

    LtsxOverlayHandle showNotification(LtsxNotificationSpec spec);

    LtsxOverlayHandle showModal(Component title, Component message);

    LtsxOverlayHandle showModal(LtsxModalSpec spec);

    void hideAll();

    void clear();

    void setLayout(int left, int top, int right, int bottom);

    interface ILtsxOverlayContent {
        default void renderBackLayer(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        void renderMainLayer(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

        default void renderFrontLayer(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        default void tick() {
        }

        default boolean isImportant() {
            return false;
        }

        default int preferredHeightPx() {
            return 0;
        }

        default void onClose() {
        }

        default void setExternalAlpha(float alpha) {
        }
    }

    interface LtsxOverlayHandle {
        void hide();

        boolean isActive();

        void dispose();

        int widthPx();

        int heightPx();

        LtsxAnchor anchor();

        void setAnchor(LtsxAnchor anchor);

        void setTargetPos(int x, int y);
    }

    interface LtsxHudOverlayHandle extends LtsxOverlayHandle {
        LtsxHudOverlayHandle setScale(float scale);

        LtsxHudOverlayHandle setContent(ILtsxOverlayContent content);

        LtsxHudOverlayHandle setContentAnimated(ILtsxOverlayContent content, LtsxTransition transition, double seconds);

        LtsxHudOverlayHandle setTitle(Component title);

        LtsxHudOverlayHandle setSlidingText(Component text);

        void show();
    }

    record LtsxNotificationSpec(
            Component title,
            Component message,
            LtsxNotificationLevel level,
            int durationTicks,
            LtsxAnchor anchor
    ) {
    }

    record LtsxModalSpec(
            Component title,
            Component message,
            int widthPx,
            int heightPx,
            LtsxAnchor anchor
    ) {
    }

    enum LtsxAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    enum LtsxTransition {
        CROSS_FADE
    }

    enum LtsxNotificationLevel {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }
}
