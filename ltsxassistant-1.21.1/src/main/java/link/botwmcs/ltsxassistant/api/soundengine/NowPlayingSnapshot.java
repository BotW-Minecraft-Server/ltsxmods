package link.botwmcs.ltsxassistant.api.soundengine;

/**
 * Immutable state snapshot for current music playback.
 */
public record NowPlayingSnapshot(
        MusicEngineMode mode,
        String albumId,
        String trackId,
        int stemTrack,
        boolean playing,
        long timelineMillis
) {
    public static NowPlayingSnapshot stopped() {
        return new NowPlayingSnapshot(MusicEngineMode.CLASSIC, "", "", -1, false, 0L);
    }
}

