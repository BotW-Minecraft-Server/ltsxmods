package link.botwmcs.core.command;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.api.module.ICoreModule;
import link.botwmcs.core.data.CoreData;
import link.botwmcs.core.module.ModuleManager;
import link.botwmcs.core.net.CoreNetwork;
import link.botwmcs.core.net.payload.OpenNetworkingStatScreenPayload;
import link.botwmcs.core.util.CoreIds;
import link.botwmcs.core.util.CoreKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Unified command root: /ltsxcore
 */
public final class CoreCommands {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private CoreCommands() {
    }

    public static void init(IEventBus neoForgeBus) {
        if (INITIALIZED.compareAndSet(false, true)) {
            neoForgeBus.addListener(CoreCommands::onRegisterCommands);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal(CoreIds.MOD_ID)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("modules")
                                .executes(context -> executeModules(context.getSource())))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("on")
                                        .executes(context -> executeDebug(context.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> executeDebug(context.getSource(), false)))
                                .then(Commands.literal("networking")
                                        .executes(context -> executeOpenNetworkingStatScreen(context.getSource()))))
        );
    }

    private static int executeModules(CommandSourceStack source) {
        final List<ICoreModule> modules = ModuleManager.getLoadedModules();
        source.sendSuccess(() -> Component.literal(CoreKeys.LOG_PREFIX + "Loaded modules: " + modules.size()), false);

        if (modules.isEmpty()) {
            source.sendSuccess(() -> Component.literal("- <none>"), false);
            return 0;
        }

        for (ICoreModule module : modules) {
            source.sendSuccess(() -> Component.literal("- " + module.moduleId() + " (order=" + module.loadOrder() + ")"), false);
        }
        return modules.size();
    }

    private static int executeDebug(CommandSourceStack source, boolean enabled) {
        CoreData.setDebugEnabled(source.getServer(), enabled);
        source.sendSuccess(() -> Component.literal(CoreKeys.LOG_PREFIX + "Debug set to " + enabled), true);
        return 1;
    }

    private static int executeOpenNetworkingStatScreen(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(CoreKeys.LOG_PREFIX + "Only players can open the networking debug GUI."));
            return 0;
        }

        CoreNetwork.sendToPlayer(player, OpenNetworkingStatScreenPayload.openScreen());
        source.sendSuccess(() -> Component.literal(CoreKeys.LOG_PREFIX + "Opened core networking debug GUI."), false);
        return 1;
    }
}
