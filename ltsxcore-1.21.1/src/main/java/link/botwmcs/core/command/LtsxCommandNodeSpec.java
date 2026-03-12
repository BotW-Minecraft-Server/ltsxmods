package link.botwmcs.core.command;

import com.mojang.brigadier.Command;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class LtsxCommandNodeSpec {
    private final String literal;
    private final Component description;
    private final List<LtsxCommandNodeSpec> children = new ArrayList<>();
    private Predicate<CommandSourceStack> requirement = source -> true;
    private Component title;
    private Command<CommandSourceStack> command;

    LtsxCommandNodeSpec(String literal, Component description) {
        this.literal = Objects.requireNonNull(literal, "literal");
        this.description = Objects.requireNonNull(description, "description");
    }

    String literal() {
        return literal;
    }

    Component description() {
        return description;
    }

    List<LtsxCommandNodeSpec> children() {
        return children;
    }

    Predicate<CommandSourceStack> requirement() {
        return requirement;
    }

    void appendRequirement(Predicate<CommandSourceStack> nextRequirement) {
        Objects.requireNonNull(nextRequirement, "nextRequirement");
        final Predicate<CommandSourceStack> current = requirement;
        requirement = source -> current.test(source) && nextRequirement.test(source);
    }

    Component title() {
        return title;
    }

    void title(Component title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    Command<CommandSourceStack> command() {
        return command;
    }

    void command(Command<CommandSourceStack> command) {
        this.command = Objects.requireNonNull(command, "command");
    }

    boolean isMenu() {
        return command == null;
    }
}
