package link.botwmcs.ltsxassistant.api.account;

/**
 * Premium verification result visible to UI.
 */
public record LittleSkinPremiumVerificationSnapshot(
        boolean available,
        boolean premiumVerified,
        String displayName,
        String statusMessage,
        long checkedAtEpochMillis
) {
    public LittleSkinPremiumVerificationSnapshot {
        displayName = displayName == null ? "" : displayName;
        statusMessage = statusMessage == null ? "" : statusMessage;
    }

    public static LittleSkinPremiumVerificationSnapshot unknown() {
        return new LittleSkinPremiumVerificationSnapshot(false, false, "", "", 0L);
    }
}
