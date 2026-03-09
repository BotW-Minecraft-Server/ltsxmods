package link.botwmcs.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.net.neb.global.CoreNebGlobalPacketUtil;
import link.botwmcs.core.net.payload.CoreNebGlobalBatchPayload;
import link.botwmcs.core.net.stat.CoreNebPacketFlowStat;
import link.botwmcs.core.net.stat.CoreNebTrafficStat;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Global inbound traffic stat hook.
 */
@Mixin(PacketDecoder.class)
public class PacketDecoderMixin {
    @Inject(
            method = "decode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/jfr/JvmProfiler;onPacketReceived(Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/protocol/PacketType;Ljava/net/SocketAddress;I)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void ltsxcore$recordGlobalInbound(
            ChannelHandlerContext context,
            ByteBuf input,
            List<Object> output,
            CallbackInfo ci,
            @Local int size,
            @Local Packet<?> packet
    ) {
        if (!CoreConfig.nebGlobalMixinEnabled()) {
            return;
        }

        Object truePacket = CoreNebGlobalPacketUtil.getTruePacket(packet);
        ResourceLocation trueType = CoreNebGlobalPacketUtil.getTrueType(packet);
        if (truePacket instanceof CoreNebGlobalBatchPayload) {
            CoreNebTrafficStat.globalInBaked(size);
            return;
        }

        if (CoreConfig.nebGlobalFullPacketStat() && !CoreNebGlobalPacketUtil.isNebTransportPacket(truePacket, trueType)) {
            CoreNebPacketFlowStat.in(trueType, size, CoreNebPacketFlowStat.TrafficPath.BYPASS);
        }
    }
}
