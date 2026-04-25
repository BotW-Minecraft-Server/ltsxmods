package link.botwmcs.ltsxassistant.api.account;

import java.util.List;

/**
 * Client-facing LittleSkin account service contract for skin workbench flows.
 */
public interface LittleSkinAccountServiceApi {
    List<String> defaultScopes();

    LittleSkinAccountSnapshot snapshot();

    void beginDeviceAuthorization();

    void cancelDeviceAuthorization();

    void refreshAccountSnapshot();

    void logout();

    void selectPlayer(String playerId);

    void applyTexture(String textureId);

    void applyClosetItem(String closetItemId);

    void clearError();
}
