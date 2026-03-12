package link.botwmcs.core.command;

import com.mojang.brigadier.Command;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import link.botwmcs.core.api.command.LtsxCommandMenuBuilder;
import link.botwmcs.core.api.command.LtsxCommandRegistrar;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

final class LtsxCommandRegistrarImpl implements LtsxCommandRegistrar {
    private static final List<String> RESERVED_LITERALS = List.of("help", "?");

    private final List<LtsxCommandNodeSpec> rootNodes = new ArrayList<>();

    @Override
    public void menu(
            String literal,
            Component description,
            Component title,
            Consumer<LtsxCommandMenuBuilder> builderConsumer
    ) {
        final LtsxCommandNodeSpec node = new LtsxCommandNodeSpec(validateLiteral(literal), description);
        if (title != null) {
            node.title(title);
        }
        addChild(rootNodes, node);
        if (builderConsumer != null) {
            builderConsumer.accept(new MenuBuilderImpl(node));
        }
    }

    List<LtsxCommandNodeSpec> rootNodes() {
        return List.copyOf(rootNodes);
    }

    private static void addChild(List<LtsxCommandNodeSpec> siblings, LtsxCommandNodeSpec node) {
        for (LtsxCommandNodeSpec sibling : siblings) {
            if (sibling.literal().equals(node.literal())) {
                throw new IllegalArgumentException("Duplicate /ltsx command literal: " + node.literal());
            }
        }
        siblings.add(node);
    }

    private static String validateLiteral(String literal) {
        final String value = Objects.requireNonNull(literal, "literal").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Command literal cannot be blank.");
        }
        if (RESERVED_LITERALS.contains(value)) {
            throw new IllegalArgumentException("Command literal is reserved by the help system: " + value);
        }
        return value;
    }

    private static final class MenuBuilderImpl implements LtsxCommandMenuBuilder {
        private final LtsxCommandNodeSpec current;

        private MenuBuilderImpl(LtsxCommandNodeSpec current) {
            this.current = current;
        }

        @Override
        public LtsxCommandMenuBuilder title(Component title) {
            current.title(title);
            return this;
        }

        @Override
        public LtsxCommandMenuBuilder requires(Predicate<CommandSourceStack> requirement) {
            current.appendRequirement(requirement);
            return this;
        }

        @Override
        public void menu(
                String literal,
                Component description,
                Component title,
                Consumer<LtsxCommandMenuBuilder> builderConsumer
        ) {
            final LtsxCommandNodeSpec child = new LtsxCommandNodeSpec(validateLiteral(literal), description);
            if (title != null) {
                child.title(title);
            }
            addChild(current.children(), child);
            if (builderConsumer != null) {
                builderConsumer.accept(new MenuBuilderImpl(child));
            }
        }

        @Override
        public void action(
                String literal,
                Component description,
                Predicate<CommandSourceStack> requirement,
                Command<CommandSourceStack> command
        ) {
            final LtsxCommandNodeSpec child = new LtsxCommandNodeSpec(validateLiteral(literal), description);
            child.appendRequirement(requirement);
            child.command(command);
            addChild(current.children(), child);
        }
    }
}
