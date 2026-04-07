package link.botwmcs.ltsxassistant.api.soundengine;

/**
 * Core playback control API for assistant music engine.
 */
public interface MusicPlaybackApi {
    void play(MusicEngineMode mode, String albumId, String trackId, int stemTrack);

    void pause();

    void resume();

    void stop();

    void setTrack(MusicEngineMode mode, String albumId, String trackId);

    void setStemTrack(int stemTrack);

    NowPlayingSnapshot nowPlaying();
}

