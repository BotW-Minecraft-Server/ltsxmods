package link.botwmcs.ltsxassistant;

import link.botwmcs.ltsxassistant.service.soundengine.AssistantAlbumCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Centralized client-only mod event bus registration.
 */
public final class LTSXAssistantClient {
    private static final ResourceLocation CLASSIC_ALBUM_PACK_PATH =
            ResourceLocation.fromNamespaceAndPath(LTSXAssistant.MODID, "resourcepacks/minecraft_classic_album");
    private static final ResourceLocation DIRECT_IMPACT_PACK_PATH =
            ResourceLocation.fromNamespaceAndPath(LTSXAssistant.MODID, "resourcepacks/direct_impact_album");


    private static boolean initialized;

    private LTSXAssistantClient() {
    }

    public static void init(IEventBus modEventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        modEventBus.addListener(LTSXAssistantClient::onAddPackFinders);
        modEventBus.addListener(LTSXAssistantClient::onRegisterClientReloadListeners);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addPackFinders(
                CLASSIC_ALBUM_PACK_PATH,
                PackType.CLIENT_RESOURCES,
                Component.translatable("pack.ltsxassistant.minecraft_classic_album"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.BOTTOM
        );
        event.addPackFinders(
                DIRECT_IMPACT_PACK_PATH,
                PackType.CLIENT_RESOURCES,
                Component.translatable("pack.ltsxassistant.direct_impact_album"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.BOTTOM
        );
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ResourceManagerReloadListener() {
            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                AssistantAlbumCatalog.markGlobalDirty();
            }
        });
    }
}
