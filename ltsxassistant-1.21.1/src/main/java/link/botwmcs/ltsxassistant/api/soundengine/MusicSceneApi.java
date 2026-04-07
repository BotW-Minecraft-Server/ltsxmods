package link.botwmcs.ltsxassistant.api.soundengine;

/**
 * Scene/context query API for music strategy routing.
 */
public interface MusicSceneApi {
    String currentSceneId();

    String currentScreenClassName();

    boolean inWorld();

    boolean underwater();

    String dimensionId();

    String biomeId();
}

