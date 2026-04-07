package link.botwmcs.ltsxassistant.client.elements;

import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import link.botwmcs.fizzy.ui.element.icon.IconElement;
import link.botwmcs.ltsxassistant.client.utils.PlayerHeadRenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Player head icon element (16x16 source) based on current logged-in account skin.
 */
public final class PlayerHeadIconElement implements ElementPainter {
    private final boolean stretchToFit;
    private final boolean allowUpscale;
    private ResourceLocation lastTexture;
    private IconElement delegate;

    public PlayerHeadIconElement() {
        this(false, false);
    }

    public PlayerHeadIconElement(boolean stretchToFit) {
        this(stretchToFit, false);
    }

    public PlayerHeadIconElement(boolean stretchToFit, boolean allowUpscale) {
        this.stretchToFit = stretchToFit;
        this.allowUpscale = allowUpscale;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        if (width <= 0 || height <= 0) {
            return;
        }

        ResourceLocation texture = PlayerHeadRenderUtils.currentPlayerHeadTexture();
        if (texture == null) {
            return;
        }
        if (!texture.equals(lastTexture) || delegate == null) {
            delegate = new IconElement(texture, stretchToFit, allowUpscale);
            lastTexture = texture;
        }
        delegate.render(guiGraphics, x, y, width, height, partialTick);
    }

    @Override
    public ElementType type() {
        return ElementType.ICON;
    }

    public static final class Builder {
        private boolean stretchToFit;
        private boolean allowUpscale;

        private Builder() {
        }

        public Builder stretchToFit(boolean stretchToFit) {
            this.stretchToFit = stretchToFit;
            return this;
        }

        public Builder stretchToFit() {
            this.stretchToFit = true;
            return this;
        }

        public Builder allowUpscale(boolean allowUpscale) {
            this.allowUpscale = allowUpscale;
            return this;
        }

        public Builder allowUpscale() {
            this.allowUpscale = true;
            return this;
        }

        public PlayerHeadIconElement build() {
            return new PlayerHeadIconElement(stretchToFit, allowUpscale);
        }
    }
}
