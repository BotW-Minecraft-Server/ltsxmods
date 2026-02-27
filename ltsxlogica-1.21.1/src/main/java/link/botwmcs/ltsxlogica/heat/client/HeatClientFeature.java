package link.botwmcs.ltsxlogica.heat.client;

import com.mojang.brigadier.Command;
import link.botwmcs.ltsxlogica.heat.network.HeatClientSyncState;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Client-only entry for heat visualization.
 *
 * This package must only be referenced from Dist.CLIENT classes.
 */
public final class HeatClientFeature {
    private static final HeatHudOverlay HUD_OVERLAY = new HeatHudOverlay();
    private static final HeatVisionOverlay VISION_OVERLAY = new HeatVisionOverlay();
    private static boolean initialized = false;

    private HeatClientFeature() {
    }

    public static void init(IEventBus forgeBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        forgeBus.addListener(HeatClientFeature::onClientTickPost);
        forgeBus.addListener(HeatClientFeature::onRenderGuiPre);
        forgeBus.addListener(HeatClientFeature::onRenderGuiPost);
        forgeBus.addListener(HeatClientFeature::onRenderLevelStage);
        forgeBus.addListener(HeatClientFeature::onRegisterClientCommands);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        HeatClientSyncState.onClientTick();
        HUD_OVERLAY.tick();
        VISION_OVERLAY.tick();
    }

    private static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        VISION_OVERLAY.renderScreenTint(event.getGuiGraphics());
    }

    private static void onRenderGuiPost(RenderGuiEvent.Post event) {
        HUD_OVERLAY.render(event.getGuiGraphics());
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        VISION_OVERLAY.render(event);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("heatvision")
                        .executes(ctx -> {
                            boolean enabled = VISION_OVERLAY.toggleEnabled();
                            ctx.getSource().sendSuccess(() -> stateMessage(enabled), false);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("on").executes(ctx -> {
                            VISION_OVERLAY.setEnabled(true);
                            ctx.getSource().sendSuccess(() -> stateMessage(true), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            VISION_OVERLAY.setEnabled(false);
                            ctx.getSource().sendSuccess(() -> stateMessage(false), false);
                            return Command.SINGLE_SUCCESS;
                        }))
        );
    }

    private static Component stateMessage(boolean enabled) {
        return Component.literal(enabled ? "Heat vision enabled." : "Heat vision disabled.");
    }
}
