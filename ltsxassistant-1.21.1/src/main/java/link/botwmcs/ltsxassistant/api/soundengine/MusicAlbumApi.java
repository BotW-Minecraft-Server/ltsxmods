package link.botwmcs.ltsxassistant.api.soundengine;

import java.util.List;

/**
 * Album catalog/query API for music resource-pack driver.
 */
public interface MusicAlbumApi {
    List<MusicAlbumDescriptor> albums();

    List<MusicTrackDescriptor> tracks(String albumId);

    String selectedAlbumId();

    void selectAlbum(String albumId);

    void playTrack(String albumId, String trackId);

    void nextTrack();

    void previousTrack();
}