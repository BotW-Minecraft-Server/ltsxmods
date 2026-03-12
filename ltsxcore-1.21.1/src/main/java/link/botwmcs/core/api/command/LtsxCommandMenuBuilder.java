package link.botwmcs.core.api.command;

import com.mojang.brigadier.Command;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Builds a menu subtree under /ltsx.
 */
public interface LtsxCommandMenuBuilder {
    LtsxCommandMenuBuilder title(Component title);

    LtsxCommandMenuBuilder requires(Predicate<CommandSourceStack> requirement);

    default void menu(String literal, Component description, Consumer<LtsxCommandMenuBuilder> builderConsumer) {
        menu(literal, description, null, builderConsumer);
    }

    void menu(
            String literal,
            Component description,
            Component title,
            Consumer<LtsxCommandMenuBuilder> builderConsumer
    );

    default void action(String literal, Component description, Command<CommandSourceStack> command) {
        action(literal, description, source -> true, command);
    }

    void action(
            String literal,
            Component description,
            Predicate<CommandSourceStack> requirement,
            Command<CommandSourceStack> command
    );
}
