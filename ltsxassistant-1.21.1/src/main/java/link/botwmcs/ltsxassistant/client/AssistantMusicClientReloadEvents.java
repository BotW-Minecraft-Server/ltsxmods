package link.botwmcs.ltsxassistant.client;

import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.service.soundengine.AssistantAlbumCatalog;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Marks album catalog dirty when client resources reload.
 */
@EventBusSubscriber(modid = LTSXAssistant.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AssistantMusicClientReloadEvents {
    private AssistantMusicClientReloadEvents() {
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ResourceManagerReloadListener() {
            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                AssistantAlbumCatalog.markGlobalDirty();
            }
        });
    }
}
