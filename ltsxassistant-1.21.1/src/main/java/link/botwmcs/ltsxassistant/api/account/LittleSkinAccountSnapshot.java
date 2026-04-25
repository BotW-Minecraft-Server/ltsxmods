package link.botwmcs.ltsxassistant.api.account;

import java.util.List;

/**
 * Immutable LittleSkin account view consumed by UI.
 */
public record LittleSkinAccountSnapshot(
        long version,
        LittleSkinConnectionState connectionState,
        boolean authenticated,
        boolean storedSessionPresent,
        String statusMessage,
        String errorMessage,
        List<String> grantedScopes,
        String selectedPlayerId,
        LittleSkinDeviceAuthSnapshot deviceAuthorization,
        List<LittleSkinPlayerSummary> players,
        List<LittleSkinTextureSummary> textures,
        List<LittleSkinClosetItemSummary> closetItems,
        LittleSkinPremiumVerificationSnapshot premiumVerification,
        List<LittleSkinYggdrasilProfileSummary> yggdrasilProfiles,
        long lastSyncEpochMillis,
        long tokenExpiresAtEpochMillis
) {
    public LittleSkinAccountSnapshot {
        connectionState = connectionState == null ? LittleSkinConnectionState.DISCONNECTED : connectionState;
        statusMessage = statusMessage == null ? "" : statusMessage;
        errorMessage = errorMessage == null ? "" : errorMessage;
        grantedScopes = grantedScopes == null ? List.of() : List.copyOf(grantedScopes);
        selectedPlayerId = selectedPlayerId == null ? "" : selectedPlayerId;
        deviceAuthorization = deviceAuthorization == null ? LittleSkinDeviceAuthSnapshot.inactive() : deviceAuthorization;
        players = players == null ? List.of() : List.copyOf(players);
        textures = textures == null ? List.of() : List.copyOf(textures);
        closetItems = closetItems == null ? List.of() : List.copyOf(closetItems);
        premiumVerification = premiumVerification == null
                ? LittleSkinPremiumVerificationSnapshot.unknown()
                : premiumVerification;
        yggdrasilProfiles = yggdrasilProfiles == null ? List.of() : List.copyOf(yggdrasilProfiles);
    }

    public static LittleSkinAccountSnapshot disconnected() {
        return new LittleSkinAccountSnapshot(
                1L,
                LittleSkinConnectionState.DISCONNECTED,
                false,
                false,
                "",
                "",
                List.of(),
                "",
                LittleSkinDeviceAuthSnapshot.inactive(),
                List.of(),
                List.of(),
                List.of(),
                LittleSkinPremiumVerificationSnapshot.unknown(),
                List.of(),
                0L,
                0L
        );
    }
}
