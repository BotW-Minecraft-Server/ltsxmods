package link.botwmcs.ltsxassistant.mixin.soundengine;

import javax.annotation.Nullable;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxassistant.api.soundengine.MusicEngineMode;
import link.botwmcs.ltsxassistant.api.soundengine.MusicPlaybackApi;
import link.botwmcs.ltsxassistant.service.soundengine.AssistantMusicEngineService;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class MixinScreenBackgroundMusic {
    @Unique
    @Nullable
    private static AssistantMusicEngineService ltsxassistant$cachedEngine;

    @Inject(method = "getBackgroundMusic", at = @At("HEAD"), cancellable = true)
    private void ltsxassistant$resolveEngineBackgroundMusic(CallbackInfoReturnable<Music> cir) {
        AssistantMusicEngineService engine = ltsxassistant$resolveEngine();
        if (engine == null) {
            return;
        }
        if (engine.nowPlaying().mode() != MusicEngineMode.MODERN) {
            return;
        }
        engine.resolveScreenBackgroundMusic((Screen) (Object) this).ifPresent(cir::setReturnValue);
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
