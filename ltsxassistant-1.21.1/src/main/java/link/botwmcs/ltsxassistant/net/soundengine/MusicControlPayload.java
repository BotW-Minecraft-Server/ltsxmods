package link.botwmcs.ltsxassistant.net.soundengine;

import java.util.Objects;
import java.util.Optional;
import link.botwmcs.core.net.CorePacketPayload;
import link.botwmcs.core.net.CorePayloadType;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.api.soundengine.MusicEngineMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Skeleton control payload used by server-side API to drive client music behavior.
 */
public record MusicControlPayload(
        String action,
        Optional<String> mode,
        Optional<String> albumId,
        Optional<String> trackId,
        Optional<Integer> stemTrack
) implements CorePacketPayload {
    public static final CorePayloadType<MusicControlPayload> TYPE =
            new CorePayloadType<>(ResourceLocation.fromNamespaceAndPath(LTSXAssistant.MODID, "music_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MusicControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            MusicControlPayload::action,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            MusicControlPayload::mode,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            MusicControlPayload::albumId,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            MusicControlPayload::trackId,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT),
            MusicControlPayload::stemTrack,
            MusicControlPayload::new
    );

    public MusicControlPayload {
        Objects.requireNonNull(action, "action");
        mode = mode == null ? Optional.empty() : mode;
        albumId = albumId == null ? Optional.empty() : albumId;
        trackId = trackId == null ? Optional.empty() : trackId;
        stemTrack = stemTrack == null ? Optional.empty() : stemTrack;
    }

    public static MusicControlPayload play(MusicEngineMode mode, String albumId, String trackId, int stemTrack) {
        return new MusicControlPayload(
                MusicControlAction.PLAY.serializedName(),
                Optional.of(mode.serializedName()),
                optionalValue(albumId),
                optionalValue(trackId),
                Optional.of(stemTrack)
        );
    }

    public static MusicControlPayload pause() {
        return new MusicControlPayload(MusicControlAction.PAUSE.serializedName(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static MusicControlPayload resume() {
        return new MusicControlPayload(MusicControlAction.RESUME.serializedName(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static MusicControlPayload stop() {
        return new MusicControlPayload(MusicControlAction.STOP.serializedName(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static MusicControlPayload setTrack(MusicEngineMode mode, String albumId, String trackId) {
        return new MusicControlPayload(
                MusicControlAction.SET_TRACK.serializedName(),
                Optional.of(mode.serializedName()),
                optionalValue(albumId),
                optionalValue(trackId),
                Optional.empty()
        );
    }

    public static MusicControlPayload setStemTrack(int stemTrack) {
        return new MusicControlPayload(
                MusicControlAction.SET_STEM.serializedName(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(stemTrack)
        );
    }

    @Override
    public CorePayloadType<MusicControlPayload> type() {
        return TYPE;
    }

    private static Optional<String> optionalValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}

