package link.botwmcs.ltsxassistant.service.account;

import java.util.List;
import link.botwmcs.ltsxassistant.api.account.LittleSkinAccountSnapshot;
import link.botwmcs.ltsxassistant.api.account.LittleSkinClosetItemSummary;
import link.botwmcs.ltsxassistant.api.account.LittleSkinConnectionState;
import link.botwmcs.ltsxassistant.api.account.LittleSkinDeviceAuthSnapshot;
import link.botwmcs.ltsxassistant.api.account.LittleSkinPlayerSummary;
import link.botwmcs.ltsxassistant.api.account.LittleSkinPremiumVerificationSnapshot;
import link.botwmcs.ltsxassistant.api.account.LittleSkinTextureSummary;
import link.botwmcs.ltsxassistant.api.account.LittleSkinYggdrasilProfileSummary;

/**
 * Mutable internal state backing the immutable UI snapshot.
 */
final class LittleSkinSessionState {
    private long version = 1L;
    private LittleSkinConnectionState connectionState = LittleSkinConnectionState.DISCONNECTED;
    private boolean authenticated;
    private boolean storedSessionPresent;
    private String statusMessage = "";
    private String errorMessage = "";
    private List<String> grantedScopes = List.of();
    private String selectedPlayerId = "";
    private LittleSkinDeviceAuthSnapshot deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
    private List<LittleSkinPlayerSummary> players = List.of();
    private List<LittleSkinTextureSummary> textures = List.of();
    private List<LittleSkinClosetItemSummary> closetItems = List.of();
    private LittleSkinPremiumVerificationSnapshot premiumVerification = LittleSkinPremiumVerificationSnapshot.unknown();
    private List<LittleSkinYggdrasilProfileSummary> yggdrasilProfiles = List.of();
    private long lastSyncEpochMillis;
    private long tokenExpiresAtEpochMillis;

    synchronized LittleSkinAccountSnapshot snapshot() {
        return new LittleSkinAccountSnapshot(
                version,
                connectionState,
                authenticated,
                storedSessionPresent,
                statusMessage,
                errorMessage,
                grantedScopes,
                selectedPlayerId,
                deviceAuthorization,
                players,
                textures,
                closetItems,
                premiumVerification,
                yggdrasilProfiles,
                lastSyncEpochMillis,
                tokenExpiresAtEpochMillis
        );
    }

    synchronized void applyStoredSessionMetadata(LittleSkinTokenStore.StoredSession storedSession) {
        if (storedSession == null) {
            storedSessionPresent = false;
            authenticated = false;
            grantedScopes = List.of();
            selectedPlayerId = "";
            deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
            closetItems = List.of();
            lastSyncEpochMillis = 0L;
            tokenExpiresAtEpochMillis = 0L;
            statusMessage = "";
            errorMessage = "";
            connectionState = LittleSkinConnectionState.DISCONNECTED;
            touch();
            return;
        }
        storedSessionPresent = true;
        authenticated = false;
        grantedScopes = storedSession.grantedScopes();
        selectedPlayerId = storedSession.selectedPlayerId();
        lastSyncEpochMillis = storedSession.lastSuccessfulSyncEpochMillis();
        tokenExpiresAtEpochMillis = storedSession.tokenExpiresAtEpochMillis();
        deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
        closetItems = List.of();
        statusMessage = "Stored LittleSkin session metadata found.";
        errorMessage = "";
        connectionState = LittleSkinConnectionState.DISCONNECTED;
        touch();
    }

    synchronized void beginRestoringSession() {
        authenticated = false;
        errorMessage = "";
        statusMessage = "Restoring LittleSkin session...";
        connectionState = LittleSkinConnectionState.RESTORING_SESSION;
        touch();
    }

    synchronized void beginRequestingDeviceCode() {
        authenticated = false;
        errorMessage = "";
        deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
        closetItems = List.of();
        statusMessage = "Requesting LittleSkin device code...";
        connectionState = LittleSkinConnectionState.REQUESTING_DEVICE_CODE;
        touch();
    }

    synchronized void showDeviceAuthorization(LittleSkinDeviceAuthSnapshot snapshot, String status) {
        authenticated = false;
        errorMessage = "";
        deviceAuthorization = snapshot == null ? LittleSkinDeviceAuthSnapshot.inactive() : snapshot;
        statusMessage = status == null ? "" : status;
        connectionState = LittleSkinConnectionState.WAITING_USER_AUTH;
        touch();
    }

    synchronized void markPollingDeviceAuthorization(LittleSkinDeviceAuthSnapshot snapshot, String status) {
        authenticated = false;
        errorMessage = "";
        deviceAuthorization = snapshot == null ? LittleSkinDeviceAuthSnapshot.inactive() : snapshot;
        statusMessage = status == null ? "" : status;
        connectionState = LittleSkinConnectionState.POLLING_TOKEN;
        touch();
    }

    synchronized void beginSyncing(List<String> scopes, long tokenExpiresAtEpochMillis, String status) {
        authenticated = true;
        storedSessionPresent = true;
        grantedScopes = scopes == null ? List.of() : List.copyOf(scopes);
        this.tokenExpiresAtEpochMillis = tokenExpiresAtEpochMillis;
        deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
        errorMessage = "";
        statusMessage = status == null ? "" : status;
        connectionState = LittleSkinConnectionState.SYNCING;
        touch();
    }

    synchronized void applyClosetReady(
            LittleSkinTokenStore.StoredSession storedSession,
            List<LittleSkinClosetItemSummary> closetItems,
            String status
    ) {
        authenticated = true;
        storedSessionPresent = storedSession != null;
        grantedScopes = storedSession == null ? grantedScopes : storedSession.grantedScopes();
        selectedPlayerId = storedSession == null ? selectedPlayerId : storedSession.selectedPlayerId();
        this.closetItems = closetItems == null ? List.of() : List.copyOf(closetItems);
        deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
        lastSyncEpochMillis = storedSession == null ? lastSyncEpochMillis : storedSession.lastSuccessfulSyncEpochMillis();
        tokenExpiresAtEpochMillis = storedSession == null ? tokenExpiresAtEpochMillis : storedSession.tokenExpiresAtEpochMillis();
        errorMessage = "";
        statusMessage = status == null ? "" : status;
        connectionState = LittleSkinConnectionState.READY;
        touch();
    }

    synchronized void markError(
            LittleSkinConnectionState fallbackState,
            boolean authenticated,
            boolean storedSessionPresent,
            LittleSkinDeviceAuthSnapshot snapshot,
            String status,
            String error
    ) {
        this.authenticated = authenticated;
        this.storedSessionPresent = storedSessionPresent;
        this.deviceAuthorization = snapshot == null ? LittleSkinDeviceAuthSnapshot.inactive() : snapshot;
        this.statusMessage = status == null ? "" : status;
        this.errorMessage = error == null ? "" : error;
        this.connectionState = fallbackState == null ? LittleSkinConnectionState.ERROR : fallbackState;
        touch();
    }

    synchronized void markDisconnected(String status) {
        authenticated = false;
        storedSessionPresent = false;
        statusMessage = status == null ? "" : status;
        errorMessage = "";
        grantedScopes = List.of();
        selectedPlayerId = "";
        deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
        closetItems = List.of();
        lastSyncEpochMillis = 0L;
        tokenExpiresAtEpochMillis = 0L;
        connectionState = LittleSkinConnectionState.DISCONNECTED;
        touch();
    }

    synchronized void setSelectedPlayer(String playerId) {
        selectedPlayerId = playerId == null ? "" : playerId;
        touch();
    }

    synchronized void cancelDeviceAuthorization() {
        deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
        statusMessage = "LittleSkin device authorization cancelled.";
        errorMessage = "";
        connectionState = authenticated ? LittleSkinConnectionState.READY : LittleSkinConnectionState.DISCONNECTED;
        touch();
    }

    synchronized void clearError() {
        errorMessage = "";
        connectionState = authenticated ? LittleSkinConnectionState.READY : LittleSkinConnectionState.DISCONNECTED;
        touch();
    }

    synchronized void clearForLogout() {
        version++;
        connectionState = LittleSkinConnectionState.DISCONNECTED;
        authenticated = false;
        storedSessionPresent = false;
        statusMessage = "LittleSkin session cleared.";
        errorMessage = "";
        grantedScopes = List.of();
        selectedPlayerId = "";
        deviceAuthorization = LittleSkinDeviceAuthSnapshot.inactive();
        players = List.of();
        textures = List.of();
        closetItems = List.of();
        premiumVerification = LittleSkinPremiumVerificationSnapshot.unknown();
        yggdrasilProfiles = List.of();
        lastSyncEpochMillis = 0L;
        tokenExpiresAtEpochMillis = 0L;
    }

    private void touch() {
        version++;
    }
}
