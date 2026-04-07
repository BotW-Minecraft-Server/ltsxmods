package link.botwmcs.ltsxassistant.mixin.soundengine;

import javax.annotation.Nullable;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxassistant.api.soundengine.MusicPlaybackApi;
import link.botwmcs.ltsxassistant.service.soundengine.AssistantMusicEngineService;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MixinMusicManager {
    @Unique
    @Nullable
    private static AssistantMusicEngineService ltsxassistant$cachedEngine;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ltsxassistant$hardTakeoverTick(CallbackInfo ci) {
        AssistantMusicEngineService engine = ltsxassistant$resolveEngine();
        if (engine != null) {
            engine.tickEngine();
        }
        ci.cancel();
    }

    @Unique
    @Nullable
    private static AssistantMusicEngineService ltsxassistant$resolveEngine() {
        if (ltsxassistant$cachedEngine != null) {
            return ltsxassistant$cachedEngine;
        }
        return CoreServices.getOptional(MusicPlaybackApi.class)
                .filter(AssistantMusicEngineService.class::isInstance)
                .map(AssistantMusicEngineService.class::cast)
                .map(engine -> {
                    ltsxassistant$cachedEngine = engine;
                    return engine;
                })
                .orElse(null);
    }
}
