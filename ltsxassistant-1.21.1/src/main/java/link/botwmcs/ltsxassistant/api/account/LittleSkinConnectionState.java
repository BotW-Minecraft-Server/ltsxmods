package link.botwmcs.ltsxassistant.api.account;

/**
 * High-level LittleSkin account state exposed to UI.
 */
public enum LittleSkinConnectionState {
    DISCONNECTED,
    RESTORING_SESSION,
    REQUESTING_DEVICE_CODE,
    WAITING_USER_AUTH,
    POLLING_TOKEN,
    SYNCING,
    READY,
    UPDATING_TEXTURE,
    ERROR;

    public boolean isBusy() {
        return switch (this) {
            case RESTORING_SESSION,
                 REQUESTING_DEVICE_CODE,
                 WAITING_USER_AUTH,
                 POLLING_TOKEN,
                 SYNCING,
                 UPDATING_TEXTURE -> true;
            case DISCONNECTED, READY, ERROR -> false;
        };
    }
}
