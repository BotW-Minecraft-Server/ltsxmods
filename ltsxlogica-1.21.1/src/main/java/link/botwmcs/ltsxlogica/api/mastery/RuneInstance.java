package link.botwmcs.ltsxlogica.api.mastery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Book-like immutable item payload; identical metadata survives bind/unbind. */
public record RuneInstance(ResourceLocation runeId, UUID instanceId) {
    public RuneInstance { Objects.requireNonNull(runeId); Objects.requireNonNull(instanceId); }
    public static final Codec<RuneInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("rune").forGetter(RuneInstance::runeId),
            Codec.STRING.comapFlatMap(value -> {
                try { return com.mojang.serialization.DataResult.success(UUID.fromString(value)); }
                catch (IllegalArgumentException ex) { return com.mojang.serialization.DataResult.error(() -> "Invalid rune instance UUID"); }
            }, UUID::toString).fieldOf("instance").forGetter(RuneInstance::instanceId)
    ).apply(instance, RuneInstance::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RuneInstance> STREAM_CODEC = new StreamCodec<>() {
        public RuneInstance decode(RegistryFriendlyByteBuf buf) { return new RuneInstance(buf.readResourceLocation(), buf.readUUID()); }
        public void encode(RegistryFriendlyByteBuf buf, RuneInstance value) { buf.writeResourceLocation(value.runeId()); buf.writeUUID(value.instanceId()); }
    };
}
