package link.botwmcs.ltsxassistant;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import link.botwmcs.ltsxassistant.api.soundengine.MusicEngineMode;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final String CLIENT_CATEGORY_SOUND_ENGINE = "soundengine";
    public static final String CLIENT_KEY_PAUSE_SCREEN_PAUSES_MUSIC = "pauseScreenPausesMusic";
    public static final String CLIENT_KEY_MUSIC_ENGINE_MODE = "musicEngineMode";
    public static final String CLIENT_KEY_SELECTED_ALBUM_ID = "selectedAlbumId";

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue PAUSE_SCREEN_PAUSES_MUSIC;
    private static final ModConfigSpec.ConfigValue<String> MUSIC_ENGINE_MODE;
    private static final ModConfigSpec.ConfigValue<String> SELECTED_ALBUM_ID;

    static {
        ModConfigSpec.Builder commonBuilder = new ModConfigSpec.Builder();
        commonBuilder.comment("General settings").push("general");
        commonBuilder.pop();
        COMMON_SPEC = commonBuilder.build();

        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        clientBuilder.comment("Client-only music engine settings").push(CLIENT_CATEGORY_SOUND_ENGINE);
        PAUSE_SCREEN_PAUSES_MUSIC = clientBuilder
                .comment("If true, opening pause screen pauses music playback.")
                .define(CLIENT_KEY_PAUSE_SCREEN_PAUSES_MUSIC, true);
        MUSIC_ENGINE_MODE = clientBuilder
                .comment("Client music mode for LTSX soundengine: classic (vanilla-style) or modern (wav/ogg resource-pack tracks).")
                .defineInList(
                        CLIENT_KEY_MUSIC_ENGINE_MODE,
                        ClientMusicEngineMode.CLASSIC.serializedName(),
                        ClientMusicEngineMode.serializedValues()
                );
        SELECTED_ALBUM_ID = clientBuilder
                .comment("Last selected album id for modern music resource-pack playback.")
                .define(CLIENT_KEY_SELECTED_ALBUM_ID, "");
        clientBuilder.pop();
        CLIENT_SPEC = clientBuilder.build();

        // Legacy alias kept for compatibility with old references.
        SPEC = COMMON_SPEC;
    }

    private Config() {
    }

    public static boolean pauseScreenPausesMusic() {
        try {
            return PAUSE_SCREEN_PAUSES_MUSIC.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    public static ClientMusicEngineMode musicEngineMode() {
        try {
            return ClientMusicEngineMode.fromSerializedName(MUSIC_ENGINE_MODE.get());
        } catch (IllegalStateException ignored) {
            return ClientMusicEngineMode.CLASSIC;
        }
    }

    public static MusicEngineMode preferredMusicEngineMode() {
        return musicEngineMode() == ClientMusicEngineMode.MODERN ? MusicEngineMode.MODERN : MusicEngineMode.CLASSIC;
    }

    public static void setMusicEngineMode(ClientMusicEngineMode mode) {
        if (mode == null) {
            return;
        }
        try {
            MUSIC_ENGINE_MODE.set(mode.serializedName());
        } catch (IllegalStateException ignored) {
            // Config may not be loaded yet during early bootstrap.
        }
    }

    public static String selectedAlbumId() {
        try {
            return SELECTED_ALBUM_ID.get();
        } catch (IllegalStateException ignored) {
            return "";
        }
    }

    public static void setSelectedAlbumId(String albumId) {
        try {
            SELECTED_ALBUM_ID.set(albumId == null ? "" : albumId);
        } catch (IllegalStateException ignored) {
            // Config may not be loaded yet during early bootstrap.
        }
    }

    public enum ClientMusicEngineMode {
        CLASSIC("classic"),
        MODERN("modern");

        private final String serializedName;

        ClientMusicEngineMode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public static ClientMusicEngineMode fromSerializedName(String raw) {
            if (raw == null || raw.isBlank()) {
                return MODERN;
            }
            String normalized = raw.toLowerCase(Locale.ROOT);
            for (ClientMusicEngineMode mode : values()) {
                if (mode.serializedName.equals(normalized)) {
                    return mode;
                }
            }
            return MODERN;
        }

        private static java.util.List<String> serializedValues() {
            return Stream.of(values())
                    .map(ClientMusicEngineMode::serializedName)
                    .collect(Collectors.toList());
        }
    }
}
