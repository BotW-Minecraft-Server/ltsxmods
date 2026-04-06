package link.botwmcs.ltsxassistant.service.client.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import link.botwmcs.fizzy.client.util.FizzyGuiUtils;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-like tab strip based on Minecraft's TabButton + TabManager.
 * This is implemented as a raw Fizzy ElementPainter in assistant scope.
 */
public final class SkinSourceTabElement implements ElementPainter {
    private static final int TAB_HEIGHT_PX = 24;
    private static final int TAB_GAP_PX = 2;
    private static final int TAB_CONTENT_GAP_PX = 2;
    private static final int SELECTED_TAB_OUTER_COLOR = 0xBF0C0D12;
    private static final int SELECTED_TAB_CENTER_COLOR = 0xBF141622;
    private static final int SELECTED_TAB_UNDERLINE_COLOR = 0xE54E515D;
    private final List<TabSpec> specs;
    private final Consumer<String> onTabChanged;
    private final String initialTabId;
    private final List<TabButton> buttons = new ArrayList<>();
    private final List<SimpleTab> tabs = new ArrayList<>();
    private TabManager tabManager;
    private String selectedTabId;

    public SkinSourceTabElement(List<TabSpec> specs, String initialTabId, Consumer<String> onTabChanged) {
        this.specs = List.copyOf(Objects.requireNonNull(specs, "specs"));
        this.initialTabId = Objects.requireNonNullElse(initialTabId, "");
        this.onTabChanged = onTabChanged == null ? ignored -> {
        } : onTabChanged;
    }

    @Override
    public void init(InitContext context, int x, int y, int width, int height) {
        buttons.clear();
        tabs.clear();
        tabManager = new TabManager(context::addRenderableWidget, widget -> {
        });

        for (TabSpec spec : specs) {
            SimpleTab tab = new SimpleTab(spec.id(), spec.title());
            tabs.add(tab);
            buttons.add(context.addRenderableWidget(new WorkbenchTabButton(tabManager, tab, x, 64)));
        }

        syncLayout(x, y, width, height);
        if (tabs.isEmpty()) {
            return;
        }
        int initialIndex = resolveInitialIndex();
        tabManager.setCurrentTab(tabs.get(initialIndex), false);
        selectedTabId = tabs.get(initialIndex).id();
        onTabChanged.accept(selectedTabId);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        if (tabManager == null || tabs.isEmpty()) {
            return;
        }
        syncLayout(x, y, width, height);
        Tab current = tabManager.getCurrentTab();
        if (current instanceof SimpleTab simpleTab && !simpleTab.id().equals(selectedTabId)) {
            selectedTabId = simpleTab.id();
            onTabChanged.accept(selectedTabId);
        }
    }

    @Override
    public ElementType type() {
        return ElementType.CUSTOM;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return new ArrayList<>(buttons);
    }

    private int resolveInitialIndex() {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id().equals(initialTabId)) {
                return i;
            }
        }
        return 0;
    }

    private void syncLayout(int x, int y, int width, int height) {
        if (tabManager == null || buttons.isEmpty()) {
            return;
        }
        int tabAreaHeight = Math.max(TAB_HEIGHT_PX, height);
        tabManager.setTabArea(new ScreenRectangle(
                x,
                y + TAB_HEIGHT_PX + TAB_CONTENT_GAP_PX,
                Math.max(1, width),
                Math.max(1, tabAreaHeight - TAB_HEIGHT_PX - TAB_CONTENT_GAP_PX)
        ));
        int buttonCount = buttons.size();
        int totalGap = Math.max(0, buttonCount - 1) * TAB_GAP_PX;
        int baseButtonWidth = Math.max(24, (Math.max(1, width) - totalGap) / buttonCount);
        int cursorX = x;
        for (int i = 0; i < buttonCount; i++) {
            int buttonWidth = i == buttonCount - 1
                    ? Math.max(24, x + width - cursorX)
                    : baseButtonWidth;
            FizzyGuiUtils.syncWidgetBounds(buttons.get(i), cursorX, y, buttonWidth, TAB_HEIGHT_PX);
            cursorX += buttonWidth + TAB_GAP_PX;
        }
    }

    private record SimpleTab(String id, Component title) implements Tab {
        @Override
        public Component getTabTitle() {
            return title;
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
        }

        @Override
        public void doLayout(ScreenRectangle screenRectangle) {
        }
    }

    /**
     * Keep vanilla TabButton state colors for normal/hovered tabs,
     * but override selected tab background to blend with the dark workbench panel.
     */
    private static final class WorkbenchTabButton extends TabButton {
        private WorkbenchTabButton(TabManager tabManager, Tab tab, int width, int height) {
            super(tabManager, tab, width, height);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            if (!isSelected()) {
                return;
            }
            int underlineWidth = Math.min(
                    Minecraft.getInstance().font.width(getMessage()),
                    Math.max(0, getWidth() - 4)
            );
            if (underlineWidth <= 0) {
                return;
            }
            int underlineX = getX() + (getWidth() - underlineWidth) / 2;
            int underlineY = getY() + getHeight() - 2;
            guiGraphics.fill(
                    underlineX,
                    underlineY,
                    underlineX + underlineWidth,
                    underlineY + 1,
                    SELECTED_TAB_UNDERLINE_COLOR
            );
        }

        @Override
        protected void renderMenuBackground(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
            if (x2 <= x1 || y2 <= y1) {
                return;
            }
            guiGraphics.fill(x1, y1, x2, y2, SELECTED_TAB_OUTER_COLOR);
            if (x2 - x1 > 2 && y2 - y1 > 2) {
                guiGraphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, SELECTED_TAB_CENTER_COLOR);
            }
        }
    }

    public record TabSpec(String id, Component title) {
        public TabSpec {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
        }
    }
}
