package link.botwmcs.core.mixin;

import java.util.Map;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.net.neb.CoreNebBlacklist;
import link.botwmcs.core.net.neb.global.CoreNebGlobalPacketPrefixHelper;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Prefix-optimized CustomPacketPayload type codec (PLAY + global mode only).
 */
@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public abstract class CustomPacketPayloadMixin {
    @Shadow
    @Final
    private ConnectionProtocol val$protocol;

    @Shadow
    @Final
    private Map<ResourceLocation, ?> val$map;

    @Redirect(
            method = "writeCap(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeResourceLocation(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/network/FriendlyByteBuf;")
    )
    private FriendlyByteBuf ltsxcore$encodePrefixType(FriendlyByteBuf buf, ResourceLocation type) {
        if (!CoreConfig.nebGlobalMixinEnabled() || this.val$protocol != ConnectionProtocol.PLAY) {
            return buf.writeResourceLocation(type);
        }
        if (CoreNebBlacklist.shouldBypassGlobal(type)) {
            return buf.writeResourceLocation(type);
        }
        CoreNebGlobalPacketPrefixHelper.get().index(type, this.val$map).save(buf);
        return buf;
    }

    @Redirect(
            method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readResourceLocation()Lnet/minecraft/resources/ResourceLocation;")
    )
    private ResourceLocation ltsxcore$decodePrefixType(FriendlyByteBuf buf) {
        if (this.val$protocol != ConnectionProtocol.PLAY) {
            return buf.readResourceLocation();
        }

        int first = buf.getUnsignedByte(buf.readerIndex()) & 0xFF;
        if (first == 0) {
            return CoreNebGlobalPacketPrefixHelper.getType(buf, this.val$map);
        }
        if ((first & 0x80) == 0) {
            return buf.readResourceLocation();
        }

        int index = buf.readerIndex();
        try {
            return CoreNebGlobalPacketPrefixHelper.getType(buf, this.val$map);
        } catch (Exception prefixDecodeException) {
            buf.readerIndex(index);
            ResourceLocation fallback = buf.readResourceLocation();
            if (fallback.getPath().isEmpty()) {
                throw new IllegalStateException("Invalid custom payload id decoded after NEB prefix fallback.", prefixDecodeException);
            }
            return fallback;
        }
    }
}
