package link.botwmcs.ltsxassistant.client.elements;

import java.util.List;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;

/**
 * Renders the vanilla global chat component into the assigned content area.
 */
public final class ShiftedGlobalChatElement implements ElementPainter {
    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || minecraft.getWindow() == null || minecraft.mouseHandler == null) {
            return;
        }
        int guiHeight = guiGraphics.guiHeight();
        int contentBottom = y + Math.max(1, height);
        int translationY = computeRenderTranslationY(guiHeight, contentBottom);
        int mouseX = toGuiMouseX(minecraft);
        int mouseY = toGuiMouseY(minecraft);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, translationY, 0.0F);
        minecraft.gui.getChat().render(guiGraphics, minecraft.gui.getGuiTicks(), mouseX, mouseY - translationY, true);
        guiGraphics.pose().popPose();
    }

    @Override
    public ElementType type() {
        return ElementType.CUSTOM;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return List.of();
    }

    public static int computeRenderTranslationY(int guiHeight, int contentBottomY) {
        int vanillaBottomY = guiHeight - 40;
        return contentBottomY - vanillaBottomY;
    }

    private static int toGuiMouseX(Minecraft minecraft) {
        return Mth.floor(
                minecraft.mouseHandler.xpos()
                        * minecraft.getWindow().getGuiScaledWidth()
                        / (double) minecraft.getWindow().getScreenWidth()
        );
    }

    private static int toGuiMouseY(Minecraft minecraft) {
        return Mth.floor(
                minecraft.mouseHandler.ypos()
                        * minecraft.getWindow().getGuiScaledHeight()
                        / (double) minecraft.getWindow().getScreenHeight()
        );
    }
}
