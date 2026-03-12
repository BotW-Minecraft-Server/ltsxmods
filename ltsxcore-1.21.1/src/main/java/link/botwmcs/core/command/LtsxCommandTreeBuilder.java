package link.botwmcs.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

final class LtsxCommandTreeBuilder {
    private static final Component ROOT_TITLE = Component.literal("LTSX");

    private LtsxCommandTreeBuilder() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildRoot(
            String rootLiteral,
            List<LtsxCommandNodeSpec> rootNodes
    ) {
        final List<String> rootPath = List.of(rootLiteral);
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(rootLiteral)
                .executes(context -> showMenuHelp(context.getSource(), rootNodes, rootPath, ROOT_TITLE, 0))
                .then(Commands.literal("help")
                        .executes(context -> showMenuHelp(context.getSource(), rootNodes, rootPath, ROOT_TITLE, 0)))
                .then(Commands.literal("?")
                        .executes(context -> showMenuHelp(context.getSource(), rootNodes, rootPath, ROOT_TITLE, 0)));

        for (LtsxCommandNodeSpec child : rootNodes) {
            root.then(buildNode(child, rootPath, ROOT_TITLE, 1));
        }
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildNode(
            LtsxCommandNodeSpec node,
            List<String> parentPath,
            Component inheritedTitle,
            int depth
    ) {
        final List<String> currentPath = appendPath(parentPath, node.literal());
        final Component effectiveTitle = node.title() != null ? node.title() : inheritedTitle;
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(node.literal())
                .requires(node.requirement());

        if (node.isMenu()) {
            builder = builder
                    .executes(context -> showMenuHelp(
                            context.getSource(),
                            node.children(),
                            currentPath,
                            effectiveTitle,
                            depth
                    ))
                    .then(Commands.literal("help")
                            .executes(context -> showMenuHelp(
                                    context.getSource(),
                                    node.children(),
                                    currentPath,
                                    effectiveTitle,
                                    depth
                            )))
                    .then(Commands.literal("?")
                            .executes(context -> showMenuHelp(
                                    context.getSource(),
                                    node.children(),
                                    currentPath,
                                    effectiveTitle,
                                    depth
                            )));

            for (LtsxCommandNodeSpec child : node.children()) {
                builder.then(buildNode(child, currentPath, effectiveTitle, depth + 1));
            }
            return builder;
        }

        return builder.executes(node.command());
    }

    private static int showMenuHelp(
            CommandSourceStack source,
            List<LtsxCommandNodeSpec> children,
            List<String> path,
            Component effectiveTitle,
            int depth
    ) {
        source.sendSuccess(() -> buildHeader(path, effectiveTitle, depth), false);

        int visible = 0;
        for (LtsxCommandNodeSpec child : children) {
            if (!child.requirement().test(source)) {
                continue;
            }
            visible++;
            final String commandPath = LtsxCommandTextFormatter.commandPath(appendPath(path, child.literal()));
            final Component line = LtsxCommandTextFormatter.helpLine(commandPath, child.description());
            source.sendSuccess(() -> line, false);
        }

        if (visible == 0) {
            source.sendSuccess(LtsxCommandTextFormatter::emptyLine, false);
        }
        return visible;
    }

    private static Component buildHeader(List<String> path, Component effectiveTitle, int depth) {
        if (depth == 0) {
            return LtsxCommandTextFormatter.helpHeader(effectiveTitle.getString() + " - Help:");
        }
        if (depth == 1) {
            return LtsxCommandTextFormatter.helpHeader(effectiveTitle.getString() + " - Help");
        }
        return LtsxCommandTextFormatter.helpHeader(
                effectiveTitle.getString() + " - " + LtsxCommandTextFormatter.commandPath(path)
        );
    }

    private static List<String> appendPath(List<String> parentPath, String literal) {
        final List<String> path = new ArrayList<>(parentPath.size() + 1);
        path.addAll(parentPath);
        path.add(literal);
        return List.copyOf(path);
    }
}
