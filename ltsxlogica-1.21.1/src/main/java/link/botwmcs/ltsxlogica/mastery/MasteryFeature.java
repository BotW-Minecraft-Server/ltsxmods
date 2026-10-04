package link.botwmcs.ltsxlogica.mastery;

import link.botwmcs.core.data.CoreData;
import link.botwmcs.ltsxlogica.data.mastery.MasteryReloadListener;
import link.botwmcs.ltsxlogica.data.persistence.mastery.PlayerMasteryData;
import link.botwmcs.ltsxlogica.mastery.network.MasteryNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.StatAwardEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class MasteryFeature {
    private static final MasteryManager MANAGER = new MasteryManager();
    private static boolean initialized;
    private MasteryFeature() {}
    public static MasteryManager manager() { return MANAGER; }
    public static void init(IEventBus bus) {
        if (initialized) return;
        initialized = true;
        MasteryNetworking.register();
        bus.addListener((AddReloadListenerEvent event) -> event.addListener(new MasteryReloadListener(MANAGER)));
        bus.addListener((StatAwardEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) MANAGER.markDirty(player);
        });
        bus.addListener((ServerTickEvent.Post event) -> MANAGER.tick(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) MANAGER.markDirty(player);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) MANAGER.remove(player);
        });
        bus.addListener((PlayerEvent.Clone event) -> CoreData.putPlayerTag(event.getEntity(), PlayerMasteryData.KEY,
                CoreData.getPlayerTag(event.getOriginal(), PlayerMasteryData.KEY).copy()));
        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) { MANAGER.markDirty(player); MasteryNetworking.sync(player, false, ""); }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) MasteryNetworking.sync(player, false, "");
        });
        bus.addListener((OnDatapackSyncEvent event) -> MANAGER.definitionsChanged());
        bus.addListener((ServerStoppedEvent event) -> MANAGER.stop());
    }
}
