package link.botwmcs.ltsxassistant.api.account;

/**
 * Device authorization state visible to UI.
 */
public record LittleSkinDeviceAuthSnapshot(
        boolean active,
        String userCode,
        String verificationUri,
        String verificationUriComplete,
        long expiresAtEpochMillis,
        int pollIntervalSeconds
) {
    public LittleSkinDeviceAuthSnapshot {
        userCode = userCode == null ? "" : userCode;
        verificationUri = verificationUri == null ? "" : verificationUri;
        verificationUriComplete = verificationUriComplete == null ? "" : verificationUriComplete;
    }

    public static LittleSkinDeviceAuthSnapshot inactive() {
        return new LittleSkinDeviceAuthSnapshot(false, "", "", "", 0L, 0);
    }
}
