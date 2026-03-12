package link.botwmcs.core.api.command;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/**
 * Registers top-level menus under the shared /ltsx command root.
 */
public interface LtsxCommandRegistrar {
    default void menu(String literal, Component description, Consumer<LtsxCommandMenuBuilder> builderConsumer) {
        menu(literal, description, null, builderConsumer);
    }

    void menu(
            String literal,
            Component description,
            Component title,
            Consumer<LtsxCommandMenuBuilder> builderConsumer
    );
}
