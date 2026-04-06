package link.botwmcs.ltsxassistant.service.client.elements;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.function.Supplier;
import link.botwmcs.fizzy.client.util.FizzyGuiUtils;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.resources.PlayerSkin;

public final class PlayerEntityElements implements ElementPainter {
    private static final int MIN_SIZE_PX = 32;
    private final Supplier<PlayerSkin> skinSupplier;
    private final boolean interactive;
    private PlayerSkinWidget widget;

    public PlayerEntityElements() {
        this(true);
    }

    public PlayerEntityElements(boolean interactive) {
        this.interactive = interactive;
        Minecraft minecraft = Minecraft.getInstance();
        GameProfile profile = new GameProfile(
                minecraft.getUser().getProfileId(),
                minecraft.getUser().getName()
        );
        this.skinSupplier = minecraft.getSkinManager().lookupInsecure(profile);
    }

    @Override
    public void init(InitContext context, int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        int size = resolveSize(width, height);
        int drawX = x + (width - size) / 2;
        int drawY = y + (height - size) / 2;
        this.widget = context.addRenderableWidget(
                new PlayerSkinWidget(drawX, drawY, minecraft.getEntityModels(), skinSupplier)
        );
        FizzyGuiUtils.syncWidgetBounds(widget, drawX, drawY, size, size);
        widget.active = interactive;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        if (widget == null) {
            return;
        }
        int size = resolveSize(width, height);
        int drawX = x + (width - size) / 2;
        int drawY = y + (height - size) / 2;
        FizzyGuiUtils.syncWidgetBounds(widget, drawX, drawY, size, size);
    }

    @Override
    public ElementType type() {
        return ElementType.CUSTOM;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return widget == null ? List.of() : List.of(widget);
    }

    private static int resolveSize(int width, int height) {
        return Math.max(MIN_SIZE_PX, Math.min(width, height));
    }
}
