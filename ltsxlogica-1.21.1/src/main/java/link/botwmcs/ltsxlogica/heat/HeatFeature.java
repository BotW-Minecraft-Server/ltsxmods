package link.botwmcs.ltsxlogica.heat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.ltsxlogica.data.HeatDataReloadListener;
import link.botwmcs.ltsxlogica.heat.network.HeatNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Public feature-part entry for LTSXLogicA heat system.
 *
 * Core module integration snippet:
 * HeatFeature.init(ctx.modBus(), ctx.neoForgeBus());
 */
public final class HeatFeature {
    private static final Map<net.minecraft.resources.ResourceKey<Level>, HeatManager> MANAGERS_BY_LEVEL = new HashMap<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private HeatFeature() {
    }

    public static void init(IEventBus modBus, IEventBus forgeBus) {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        HeatNetworking.registerCorePayloads();

        forgeBus.addListener(HeatFeature::onLevelTickPost);
        forgeBus.addListener(HeatFeature::onLevelSave);
        forgeBus.addListener(HeatFeature::onLevelUnload);
        forgeBus.addListener(HeatFeature::onChunkLoad);
        forgeBus.addListener(HeatFeature::onChunkUnload);
        forgeBus.addListener(HeatFeature::onPlayerLoggedOut);
        forgeBus.addListener(HeatFeature::onBlockPlace);
        forgeBus.addListener(HeatFeature::onBlockBreak);
        forgeBus.addListener(HeatFeature::onBlockToolModify);
        forgeBus.addListener(HeatFeature::onFluidPlace);
        forgeBus.addListener(HeatFeature::onNeighborNotify);
        forgeBus.addListener(HeatFeature::onAddReloadListeners);
        forgeBus.addListener(HeatFeature::onServerStopped);
    }

    public static HeatManager getManager(ServerLevel level) {
        return MANAGERS_BY_LEVEL.computeIfAbsent(level.dimension(), key -> new HeatManager());
    }

    private static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        getManager(level).tick(level);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        getManager(level).onChunkLoad(level, pos.x, pos.z);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        getManager(level).onChunkUnload(level, pos.x, pos.z);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        HeatManager manager = MANAGERS_BY_LEVEL.get(level.dimension());
        if (manager != null) {
            manager.onPlayerLoggedOut(player);
        }
    }

    private static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        HeatManager manager = MANAGERS_BY_LEVEL.get(level.dimension());
        if (manager != null) {
            manager.flushPersistence(level, false);
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        HeatManager manager = MANAGERS_BY_LEVEL.remove(level.dimension());
        if (manager != null) {
            manager.flushPersistence(level, true);
        }
    }

    private static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState oldState = event.getBlockSnapshot().getState();
        BlockState newState = event.getPlacedBlock();
        getManager(level).onBlockChanged(level, event.getPos(), oldState, newState);
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        getManager(level).onBlockChanged(level, event.getPos(), event.getState(), Blocks.AIR.defaultBlockState());
    }

    private static void onBlockToolModify(BlockEvent.BlockToolModificationEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.isSimulated()) {
            return;
        }
        BlockState oldState = event.getState();
        BlockState newState = event.getFinalState();
        if (newState == null) {
            return;
        }
        getManager(level).onBlockChanged(level, event.getPos(), oldState, newState);
    }

    private static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        getManager(level).onBlockChanged(level, event.getPos(), event.getOriginalState(), event.getNewState());
    }

    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        getManager(level).onPotentialStateUpdate(level, event.getPos(), event.getState());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            HeatManager manager = MANAGERS_BY_LEVEL.get(level.dimension());
            if (manager != null) {
                manager.flushPersistence(level, true);
            }
        }
        MANAGERS_BY_LEVEL.clear();
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new HeatDataReloadListener());
    }
}
