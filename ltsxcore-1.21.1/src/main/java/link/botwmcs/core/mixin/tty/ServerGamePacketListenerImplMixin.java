package link.botwmcs.core.mixin.tty;

import link.botwmcs.core.service.tty.TtyBootstrap;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin implements ServerPlayerConnection {
    @Inject(method = "handleChatCommand", at = @At("HEAD"))
    private void ltsxcore$ttyLogExecutedCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        TtyBootstrap.logPlayerCommand(this.getPlayer().getGameProfile().getName(), packet.command());
    }

    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"))
    private void ltsxcore$ttyLogExecutedSignedCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
        TtyBootstrap.logPlayerCommand(this.getPlayer().getGameProfile().getName(), packet.command());
    }
}
