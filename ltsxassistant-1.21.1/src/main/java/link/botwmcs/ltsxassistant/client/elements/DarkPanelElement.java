package link.botwmcs.ltsxassistant.client.elements;

import java.util.List;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Simple dark panel painter used as card/container background.
 */
public final class DarkPanelElement implements ElementPainter {
    private final int innerColor;
    private final int centerColor;

    public DarkPanelElement(int innerColor, int centerColor) {
        this.innerColor = innerColor;
        this.centerColor = centerColor;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        int x2 = x + width;
        int y2 = y + height;
        guiGraphics.fill(x, y, x2, y2, innerColor);
        guiGraphics.fill(x + 1, y + 1, x2 - 1, y2 - 1, centerColor);
        guiGraphics.fill(x, y, x2, y + 1, 0xE54E515D);
        guiGraphics.fill(x, y2 - 1, x2, y2, 0xC50A0B0F);
        guiGraphics.fill(x, y, x + 1, y2, 0xDA505361);
        guiGraphics.fill(x2 - 1, y, x2, y2, 0xC308090D);
    }

    @Override
    public ElementType type() {
        return ElementType.CUSTOM;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return List.of();
    }
}
