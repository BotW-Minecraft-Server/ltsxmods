package link.botwmcs.ltsxassistant.service.soundengine;

import link.botwmcs.core.net.CoreNetwork;
import link.botwmcs.ltsxassistant.api.soundengine.MusicEngineMode;
import link.botwmcs.ltsxassistant.api.soundengine.MusicServerControlApi;
import link.botwmcs.ltsxassistant.net.soundengine.MusicControlPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side control bridge for assistant music playback.
 */
public final class AssistantMusicServerControlService implements MusicServerControlApi {
    @Override
    public void sendPlay(ServerPlayer player, MusicEngineMode mode, String albumId, String trackId, int stemTrack) {
        CoreNetwork.playerConnection(player).send(MusicControlPayload.play(mode, albumId, trackId, stemTrack));
    }

    @Override
    public void sendPause(ServerPlayer player) {
        CoreNetwork.playerConnection(player).send(MusicControlPayload.pause());
    }

    @Override
    public void sendResume(ServerPlayer player) {
        CoreNetwork.playerConnection(player).send(MusicControlPayload.resume());
    }

    @Override
    public void sendStop(ServerPlayer player) {
        CoreNetwork.playerConnection(player).send(MusicControlPayload.stop());
    }

    @Override
    public void sendSetTrack(ServerPlayer player, MusicEngineMode mode, String albumId, String trackId) {
        CoreNetwork.playerConnection(player).send(MusicControlPayload.setTrack(mode, albumId, trackId));
    }

    @Override
    public void sendSetStemTrack(ServerPlayer player, int stemTrack) {
        CoreNetwork.playerConnection(player).send(MusicControlPayload.setStemTrack(stemTrack));
    }
}

