package link.botwmcs.ltsxassistant.mixin.chat;

import link.botwmcs.ltsxassistant.client.screen.LtsxChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponentFocus {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "isChatFocused", at = @At("HEAD"), cancellable = true)
    private void ltsxassistant$includeLtsxChatScreen(CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.screen instanceof LtsxChatScreen) {
            cir.setReturnValue(true);
        }
    }
}
