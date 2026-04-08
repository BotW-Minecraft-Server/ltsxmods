package link.botwmcs.ltsxassistant.api.soundengine;

/**
 * Lightweight track entry visible to UI/API callers.
 */
public record MusicTrackDescriptor(
        String albumId,
        String trackId,
        String displayName,
        int trackNumber,
        int stemPairs,
        String format
) {
}