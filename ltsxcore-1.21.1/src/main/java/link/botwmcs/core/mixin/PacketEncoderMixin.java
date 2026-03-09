package link.botwmcs.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.net.neb.global.CoreNebGlobalPacketUtil;
import link.botwmcs.core.net.payload.CoreNebGlobalBatchPayload;
import link.botwmcs.core.net.stat.CoreNebPacketFlowStat;
import link.botwmcs.core.net.stat.CoreNebTrafficStat;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Global outbound traffic stat hook.
 */
@Mixin(PacketEncoder.class)
public class PacketEncoderMixin {
    @Inject(
            method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/jfr/JvmProfiler;onPacketSent(Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/protocol/PacketType;Ljava/net/SocketAddress;I)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void ltsxcore$recordGlobalOutbound(
            ChannelHandlerContext context,
            Packet<?> packet,
            ByteBuf output,
            CallbackInfo ci,
            @Local int size
    ) {
        if (!CoreConfig.nebGlobalMixinEnabled()) {
            return;
        }

        Object truePacket = CoreNebGlobalPacketUtil.getTruePacket(packet);
        ResourceLocation trueType = CoreNebGlobalPacketUtil.getTrueType(packet);

        if (truePacket instanceof CoreNebGlobalBatchPayload payload && payload.rawSizeHint() > 0) {
            CoreNebTrafficStat.globalOutBaked(size);
            CoreNebTrafficStat.globalOutRaw(payload.rawSizeHint());
            return;
        }

        if (CoreConfig.nebGlobalFullPacketStat() && !CoreNebGlobalPacketUtil.isNebTransportPacket(truePacket, trueType)) {
            CoreNebPacketFlowStat.out(trueType, size, CoreNebPacketFlowStat.TrafficPath.BYPASS);
        }
    }
}
