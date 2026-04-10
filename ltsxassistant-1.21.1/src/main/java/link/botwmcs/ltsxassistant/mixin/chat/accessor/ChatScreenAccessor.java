package link.botwmcs.ltsxassistant.mixin.chat.accessor;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor("initial")
    String ltsxassistant$getInitial();
}
