package link.botwmcs.ltsxassistant.api.soundengine;

import java.util.Locale;

/**
 * Playback mode split for the assistant music engine.
 */
public enum MusicEngineMode {
    CLASSIC("classic"),
    MODERN("modern");

    private final String serializedName;

    MusicEngineMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MusicEngineMode fromSerializedName(String raw) {
        if (raw == null || raw.isBlank()) {
            return CLASSIC;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (MusicEngineMode value : values()) {
            if (value.serializedName.equals(normalized)) {
                return value;
            }
        }
        return CLASSIC;
    }
}

