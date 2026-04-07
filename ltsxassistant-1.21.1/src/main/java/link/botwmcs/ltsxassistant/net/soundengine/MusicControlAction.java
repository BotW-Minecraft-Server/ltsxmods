package link.botwmcs.ltsxassistant.net.soundengine;

import java.util.Locale;

public enum MusicControlAction {
    PLAY("play"),
    PAUSE("pause"),
    RESUME("resume"),
    STOP("stop"),
    SET_TRACK("set_track"),
    SET_STEM("set_stem");

    private final String serializedName;

    MusicControlAction(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MusicControlAction fromSerializedName(String raw) {
        if (raw == null || raw.isBlank()) {
            return STOP;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (MusicControlAction value : values()) {
            if (value.serializedName.equals(normalized)) {
                return value;
            }
        }
        return STOP;
    }
}

