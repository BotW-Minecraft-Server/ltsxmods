package link.botwmcs.ltsxassistant.api.chat;

import link.botwmcs.fizzy.ui.element.ElementPainter;

/**
 * Factory for creating a page element instance for the current screen lifecycle.
 */
@FunctionalInterface
public interface ChatPageElementFactory {
    ElementPainter create();
}
