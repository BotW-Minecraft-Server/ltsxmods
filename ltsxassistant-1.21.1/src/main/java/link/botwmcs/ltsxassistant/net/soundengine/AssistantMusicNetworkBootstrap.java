package link.botwmcs.ltsxassistant.net.soundengine;

import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.net.CoreNetwork;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxassistant.api.soundengine.MusicPlaybackApi;
import link.botwmcs.ltsxassistant.service.soundengine.AssistantMusicEngineService;
import org.slf4j.Logger;

/**
 * Registers assistant sound engine payloads on CoreNetwork.
 */
public final class AssistantMusicNetworkBootstrap {
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    private AssistantMusicNetworkBootstrap() {
    }

    public static void bootstrap(Logger logger) {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }
        CoreNetwork.registerPlayToClient(MusicControlPayload.TYPE, MusicControlPayload.STREAM_CODEC, AssistantMusicNetworkBootstrap::handleControl);
        logger.info("[ltsxassistant] Registered assistant music network payloads.");
    }

    private static void handleControl(MusicControlPayload payload, link.botwmcs.core.net.CorePayloadContext context) {
        context.enqueueWork(() -> CoreServices.getOptional(MusicPlaybackApi.class).ifPresent(api -> {
            if (api instanceof AssistantMusicEngineService engineService) {
                engineService.applyControlPayload(payload);
            }
        }));
    }
}

