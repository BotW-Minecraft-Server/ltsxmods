package link.botwmcs.core.mixin.tty;

import link.botwmcs.core.service.tty.TtyBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"net.minecraft.server.dedicated.DedicatedServer$1"})
abstract class DedicatedServerConsoleThreadMixin {
    @Inject(method = "run()V", at = @At("HEAD"), cancellable = true)
    private void ltsxcore$ttyCancelVanillaConsoleThread(CallbackInfo info) {
        if (!TtyBootstrap.shouldReplaceVanillaConsole()) {
            return;
        }
        TtyBootstrap.onVanillaConsoleThreadIntercepted();
        info.cancel();
    }
}
