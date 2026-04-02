package link.botwmcs.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.api.command.LtsxCommandRegistrar;
import link.botwmcs.core.api.module.ICoreModule;
import link.botwmcs.core.data.CoreData;
import link.botwmcs.core.module.ModuleManager;
import link.botwmcs.core.net.CoreNetwork;
import link.botwmcs.core.net.payload.OpenNetworkingStatScreenPayload;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.core.service.tty.TtyService;
import link.botwmcs.core.util.CoreIds;
import link.botwmcs.core.util.CoreKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * Unified command root: /ltsx
 */
public final class CoreCommands {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Logger LOGGER = LogUtils.getLogger();

    private CoreCommands() {
    }

    public static void init(IEventBus neoForgeBus) {
        if (INITIALIZED.compareAndSet(false, true)) {
            neoForgeBus.addListener(CoreCommands::onRegisterCommands);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        final LtsxCommandRegistrarImpl registrar = new LtsxCommandRegistrarImpl();

        registerCoreMenu(registrar);
        registerModuleMenus(registrar);

        dispatcher.register(LtsxCommandTreeBuilder.buildRoot(CoreIds.ROOT_COMMAND, registrar.rootNodes()));
    }

    private static void registerCoreMenu(LtsxCommandRegistrar registrar) {
        registrar.menu(
                "core",
                Component.literal("Core Modules"),
                Component.literal("LTSX Core"),
                core -> {
                    core.action(
                            "modules",
                            Component.literal("List loaded modules"),
                            context -> executeModules(context.getSource())
                    );
                    core.menu(
                            "console",
                            Component.literal("TTY console service"),
                            console -> console.action(
                                    "status",
                                    Component.literal("Show TTY console status"),
                                    context -> executeConsoleStatus(context.getSource())
                            )
                    );
                    core.menu(
                            "debug",
                            Component.literal("Core debug commands"),
                            debug -> {
                                debug.action(
                                        "on",
                                        Component.literal("Enable debug mode"),
                                        source -> source.hasPermission(2),
                                        context -> executeDebug(context.getSource(), true)
                                );
                                debug.action(
                                        "off",
                                        Component.literal("Disable debug mode"),
                                        source -> source.hasPermission(2),
                                        context -> executeDebug(context.getSource(), false)
                                );
                                debug.action(
                                        "networking",
                                        Component.literal("Open networking debug GUI"),
                                        source -> source.hasPermission(2),
                                        context -> executeOpenNetworkingStatScreen(context.getSource())
                                );
                            }
                    );
                }
        );
    }

    private static void registerModuleMenus(LtsxCommandRegistrar registrar) {
        for (ICoreModule module : ModuleManager.getLoadedModules()) {
            try {
                module.registerLtsxCommands(registrar);
            } catch (Throwable throwable) {
                LOGGER.error("{}Failed to register /{} commands for module '{}'.",
                        CoreKeys.LOG_PREFIX,
                        CoreIds.ROOT_COMMAND,
                        module.moduleId(),
                        throwable);
            }
        }
    }

    private static int executeModules(CommandSourceStack source) {
        final List<ICoreModule> modules = ModuleManager.getLoadedModules();
        source.sendSuccess(
                () -> Component.literal("LTSX Core - Loaded Modules: " + modules.size()).withStyle(ChatFormatting.YELLOW),
                false
        );

        if (modules.isEmpty()) {
            source.sendSuccess(LtsxCommandTextFormatter::emptyLine, false);
            return 0;
        }

        for (ICoreModule module : modules) {
            final Component line = Component.empty()
                    .append(Component.literal("- ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(module.moduleId()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (order=" + module.loadOrder() + ")").withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> line, false);
        }
        return modules.size();
    }

    private static int executeConsoleStatus(CommandSourceStack source) {
        final TtyService.Status status = CoreServices.getOptional(TtyService.class)
                .map(TtyService::status)
                .orElseGet(TtyService.Status::unsupported);

        source.sendSuccess(
                () -> Component.literal("LTSX Core - TTY Console").withStyle(ChatFormatting.YELLOW),
                false
        );
        source.sendSuccess(
                () -> Component.literal("- supported=" + status.supported()).withStyle(ChatFormatting.WHITE),
                false
        );
        source.sendSuccess(
                () -> Component.literal("- enabled=" + status.enabled()).withStyle(ChatFormatting.WHITE),
                false
        );
        source.sendSuccess(
                () -> Component.literal("- installed=" + status.installed()).withStyle(ChatFormatting.WHITE),
                false
        );
        source.sendSuccess(
                () -> Component.literal("- running=" + status.running()).withStyle(ChatFormatting.WHITE),
                false
        );
        source.sendSuccess(
                () -> Component.literal("- logPlayerCommands=" + status.logPlayerCommands()).withStyle(ChatFormatting.WHITE),
                false
        );
        source.sendSuccess(
                () -> Component.literal("- historyFile=" + status.historyFile()).withStyle(ChatFormatting.GRAY),
                false
        );
        return status.running() ? 1 : 0;
    }

    private static int executeDebug(CommandSourceStack source, boolean enabled) {
        CoreData.setDebugEnabled(source.getServer(), enabled);
        source.sendSuccess(
                () -> Component.literal("LTSX Core - Debug set to " + enabled)
                        .withStyle(enabled ? ChatFormatting.YELLOW : ChatFormatting.WHITE),
                true
        );
        return 1;
    }

    private static int executeOpenNetworkingStatScreen(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("LTSX Core - Only players can open the networking debug GUI.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        CoreNetwork.sendToPlayer(player, OpenNetworkingStatScreenPayload.openScreen());
        source.sendSuccess(
                () -> Component.literal("LTSX Core - Opened networking debug GUI.").withStyle(ChatFormatting.YELLOW),
                false
        );
        return 1;
    }

}
