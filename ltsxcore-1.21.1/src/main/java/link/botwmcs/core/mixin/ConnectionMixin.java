package link.botwmcs.core.mixin;

import io.netty.channel.local.LocalAddress;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.net.neb.CoreNebBlacklist;
import link.botwmcs.core.net.neb.global.CoreNebGlobalAggregationManager;
import link.botwmcs.core.net.neb.global.CoreNebGlobalPacketUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Global packet aggregation takeover on Connection send path.
 */
@Mixin(value = Connection.class, priority = 1)
public abstract class ConnectionMixin {
    @Shadow
    private volatile PacketListener packetListener;

    @Shadow
    public abstract void send(Packet<?> packet, PacketSendListener listener, boolean flush);

    @Shadow
    public abstract boolean isMemoryConnection();

    @Shadow
    public abstract java.net.SocketAddress getRemoteAddress();

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void ltsxcore$nebGlobalTakeOver(Packet<?> packet, PacketSendListener listener, boolean flush, CallbackInfo ci) {
        if (!CoreConfig.nebGlobalMixinEnabled()) {
            return;
        }
        if (this.packetListener == null || this.packetListener.protocol() != ConnectionProtocol.PLAY) {
            return;
        }
        if (this.isMemoryConnection() || this.getRemoteAddress() instanceof LocalAddress) {
            return;
        }

        Connection connection = (Connection) (Object) this;
        if (!CoreNebGlobalAggregationManager.canTakeOverPlay(connection)) {
            return;
        }

        if (CoreNebBlacklist.shouldBypassGlobal(CoreNebGlobalPacketUtil.getTrueType(packet))) {
            CoreNebGlobalAggregationManager.flushConnection(connection);
            return;
        }

        if (packet instanceof BundlePacket<?> bundlePacket) {
            for (Packet<?> subPacket : bundlePacket.subPackets()) {
                this.send(subPacket, listener, flush);
            }
            ci.cancel();
            return;
        }

        CoreNebGlobalAggregationManager.takeOver(packet, connection);
        ci.cancel();
    }
}
