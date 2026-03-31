package link.botwmcs.core.api.fizzier.gui;

import java.util.function.Consumer;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public interface IFizzyGuiService {
    LtsxGuiBuilder builder();

    LtsxFrame fizzyFrame(Component title);

    LtsxFrame fizzyFrame(Component title, boolean drawTitle);

    LtsxFrame motiveFrame(Component title);

    LtsxFrame motiveFrame(Component title, boolean drawTitle);

    LtsxBackground background(LtsxBackgroundType type);

    LtsxBackground solidBackground(int argb);

    LtsxBehind blurBehind();

    LtsxBehind vanillaBehind();

    LtsxBehind solidBehind(int argb);

    LtsxBehind imageBehind(ResourceLocation texture);

    Screen createScreen(LtsxGui gui);

    <T extends AbstractContainerMenu> AbstractContainerScreen<T> createMenuScreen(
            T menu,
            Inventory inventory,
            Component title,
            LtsxGui gui
    );

    interface LtsxGui {
        int widthPx();

        int heightPx();
    }

    interface LtsxFrame {
    }

    interface LtsxBackground {
    }

    interface LtsxBehind {
    }

    interface LtsxGuiBuilder {
        LtsxGuiBuilder sizeSlots(int slots);

        LtsxGuiBuilder sizeSlots(int columns, int rows);

        LtsxGuiBuilder host(LtsxGuiHostType hostType);

        LtsxGuiBuilder frame(LtsxFrame frame);

        LtsxGuiBuilder background(LtsxBackground background);

        LtsxGuiBuilder behind(LtsxBehind behind);

        LtsxGuiBuilder below(IFizzyElementService.LtsxElement element);

        LtsxGuiBuilder overrideSizePx(int width, int height);

        LtsxGuiBuilder pad(int rowStart, int colStart, int rowEnd, int colEnd, Consumer<LtsxPadBuilder> configurer);

        LtsxGuiBuilder padAuto(int rowStart, int colStart, int rowEnd, int colEnd, Consumer<LtsxPadBuilder> configurer);

        LtsxGuiBuilder padByPx(int x, int y, int width, int height, Consumer<LtsxPadBuilder> configurer);

        LtsxGuiBuilder padByFrame(Consumer<LtsxPadBuilder> configurer);

        LtsxGuiBuilder split(int rowStart, int colStart, int rowEnd, int colEnd);

        LtsxGuiBuilder splitByPx(int startPx, int endPx, int atPx, LtsxSplitType splitType);

        LtsxGui build();
    }

    interface LtsxPadBuilder {
        LtsxPadBuilder element(IFizzyElementService.LtsxElement element);

        LtsxPadBuilder elements(IFizzyElementService.LtsxElement... elements);

        LtsxPadBuilder inner();
    }

    enum LtsxGuiHostType {
        SCREEN,
        MENU
    }

    enum LtsxBackgroundType {
        STONE,
        BARRIER,
        BARRIER_BLUE,
        PURE_GRAY,
        BOTW
    }

    enum LtsxSplitType {
        VERTICAL,
        HORIZONTAL
    }
}
