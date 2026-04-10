package link.botwmcs.ltsxassistant.mixin.chat;

import javax.annotation.Nullable;
import link.botwmcs.ltsxassistant.client.screen.LtsxChatScreen;
import link.botwmcs.ltsxassistant.mixin.chat.accessor.ChatScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftChatScreenRedirect {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void ltsxassistant$replaceVanillaChatScreen(@Nullable Screen guiScreen, CallbackInfo ci) {
        if (guiScreen == null || guiScreen.getClass() != ChatScreen.class) {
            return;
        }

        String initial = ((ChatScreenAccessor) guiScreen).ltsxassistant$getInitial();
        ((Minecraft) (Object) this).setScreen(new LtsxChatScreen(initial));
        ci.cancel();
    }
}
