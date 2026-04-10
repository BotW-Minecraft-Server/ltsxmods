package link.botwmcs.ltsxassistant.client.elements;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import link.botwmcs.ltsxassistant.api.chat.ChatPageElementDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.slf4j.Logger;

/**
 * Content container that can switch between multiple pages, where each page can host multiple elements.
 */
public final class SwappableContentElement implements ElementPainter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, List<ChatPageElementDefinition>> pageDefinitions;
    private final Supplier<String> activePageIdSupplier;
    @Nullable
    private final String fallbackPageId;
    private final Map<String, List<ResolvedElement>> resolvedPages = new LinkedHashMap<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();

    private int rootX;
    private int rootY;
    private int rootWidth;
    private int rootHeight;
    private String appliedActivePage = "";

    public SwappableContentElement(
            Map<String, List<ChatPageElementDefinition>> pageDefinitions,
            Supplier<String> activePageIdSupplier,
            @Nullable String fallbackPageId
    ) {
        this.pageDefinitions = new LinkedHashMap<>();
        Objects.requireNonNull(pageDefinitions, "pageDefinitions").forEach((id, definitions) -> {
            if (id != null && !id.isBlank()) {
                this.pageDefinitions.put(id, definitions == null ? List.of() : List.copyOf(definitions));
            }
        });
        this.activePageIdSupplier = Objects.requireNonNull(activePageIdSupplier, "activePageIdSupplier");
        this.fallbackPageId = fallbackPageId;
    }

    @Override
    public void init(InitContext context, int x, int y, int width, int height) {
        this.rootX = x;
        this.rootY = y;
        this.rootWidth = Math.max(1, width);
        this.rootHeight = Math.max(1, height);
        this.resolvedPages.clear();
        this.widgets.clear();

        for (Map.Entry<String, List<ChatPageElementDefinition>> entry : pageDefinitions.entrySet()) {
            String pageId = entry.getKey();
            List<ResolvedElement> resolvedElements = new ArrayList<>();
            for (ChatPageElementDefinition elementDefinition : entry.getValue()) {
                if (elementDefinition == null) {
                    continue;
                }
                ElementPainter painter;
                try {
                    painter = elementDefinition.factory().create();
                } catch (Throwable throwable) {
                    LOGGER.warn("Failed to create advanced chat page element. page={}", pageId, throwable);
                    continue;
                }
                if (painter == null) {
                    continue;
                }

                ElementBounds bounds = resolveBounds(elementDefinition, this.rootX, this.rootY, this.rootWidth, this.rootHeight);
                List<AbstractWidget> localWidgets = new ArrayList<>();
                painter.init(new ChildInitContext(context, localWidgets), bounds.x(), bounds.y(), bounds.width(), bounds.height());
                this.widgets.addAll(localWidgets);
                resolvedElements.add(new ResolvedElement(elementDefinition, painter, localWidgets));
            }
            this.resolvedPages.put(pageId, resolvedElements);
        }

        String active = resolveActivePageId();
        this.appliedActivePage = active;
        updateWidgetVisibility(active);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        this.rootX = x;
        this.rootY = y;
        this.rootWidth = Math.max(1, width);
        this.rootHeight = Math.max(1, height);

        String active = resolveActivePageId();
        if (!active.equals(this.appliedActivePage)) {
            this.appliedActivePage = active;
            updateWidgetVisibility(active);
        }

        List<ResolvedElement> elements = this.resolvedPages.get(active);
        if (elements == null || elements.isEmpty()) {
            return;
        }
        for (ResolvedElement resolvedElement : elements) {
            ElementBounds bounds = resolveBounds(
                    resolvedElement.definition(),
                    this.rootX,
                    this.rootY,
                    this.rootWidth,
                    this.rootHeight
            );
            resolvedElement.painter().render(
                    guiGraphics,
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    partialTick
            );
        }
    }

    @Override
    public ElementType type() {
        return ElementType.CUSTOM;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return this.widgets;
    }

    private String resolveActivePageId() {
        String requested = this.activePageIdSupplier.get();
        if (requested != null && this.resolvedPages.containsKey(requested)) {
            return requested;
        }
        if (this.fallbackPageId != null && this.resolvedPages.containsKey(this.fallbackPageId)) {
            return this.fallbackPageId;
        }
        if (!this.resolvedPages.isEmpty()) {
            return this.resolvedPages.keySet().iterator().next();
        }
        return "";
    }

    private void updateWidgetVisibility(String activePageId) {
        for (Map.Entry<String, List<ResolvedElement>> entry : this.resolvedPages.entrySet()) {
            boolean visible = entry.getKey().equals(activePageId);
            for (ResolvedElement resolvedElement : entry.getValue()) {
                for (AbstractWidget widget : resolvedElement.widgets()) {
                    widget.visible = visible;
                    widget.active = visible;
                }
            }
        }
    }

    private static ElementBounds resolveBounds(
            ChatPageElementDefinition definition,
            int rootX,
            int rootY,
            int rootWidth,
            int rootHeight
    ) {
        int x = rootX + definition.x();
        int y = rootY + definition.y();
        int width = definition.fillWidth() ? rootWidth : Math.max(1, definition.width());
        int height = definition.fillHeight() ? rootHeight : Math.max(1, definition.height());
        return new ElementBounds(x, y, width, height);
    }

    private record ResolvedElement(
            ChatPageElementDefinition definition,
            ElementPainter painter,
            List<AbstractWidget> widgets
    ) {
    }

    private record ElementBounds(int x, int y, int width, int height) {
    }

    private record ChildInitContext(InitContext parent, List<AbstractWidget> localWidgets) implements InitContext {
        @Override
        public <T extends AbstractWidget> T addRenderableWidget(T widget) {
            T added = parent.addRenderableWidget(widget);
            localWidgets.add(added);
            return added;
        }
    }
}
