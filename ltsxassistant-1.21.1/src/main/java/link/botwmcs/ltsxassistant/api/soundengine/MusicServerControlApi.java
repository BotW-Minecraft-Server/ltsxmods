package link.botwmcs.ltsxassistant.api.soundengine;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side bridge that controls client music playback via network payloads.
 */
public interface MusicServerControlApi {
    void sendPlay(ServerPlayer player, MusicEngineMode mode, String albumId, String trackId, int stemTrack);

    void sendPause(ServerPlayer player);

    void sendResume(ServerPlayer player);

    void sendStop(ServerPlayer player);

    void sendSetTrack(ServerPlayer player, MusicEngineMode mode, String albumId, String trackId);

    void sendSetStemTrack(ServerPlayer player, int stemTrack);
}

