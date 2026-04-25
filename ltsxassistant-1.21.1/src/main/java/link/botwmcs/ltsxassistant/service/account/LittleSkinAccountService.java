package link.botwmcs.ltsxassistant.service.account;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.api.account.LittleSkinAccountServiceApi;
import link.botwmcs.ltsxassistant.api.account.LittleSkinAccountSnapshot;
import link.botwmcs.ltsxassistant.api.account.LittleSkinClosetItemSummary;
import link.botwmcs.ltsxassistant.api.account.LittleSkinConnectionState;
import link.botwmcs.ltsxassistant.api.account.LittleSkinDeviceAuthSnapshot;
import net.minecraft.client.Minecraft;

/**
 * LittleSkin account service implementation for the device-code login flow.
 */
public final class LittleSkinAccountService implements LittleSkinAccountServiceApi {
    private static final List<String> DEFAULT_SCOPES = List.of(
            "offline_access",
            "Closet.Read"
    );
    private static final String IO_THREAD_NAME = "LTSXAssistant-LittleSkin-IO";
    private static final long TOKEN_EXPIRY_SAFETY_WINDOW_MILLIS = 30_000L;
    private static final LittleSkinApiClient.ClosetQuery DEFAULT_CLOSET_QUERY =
            new LittleSkinApiClient.ClosetQuery("skin", "", 24, 1);

    private final LittleSkinApiClient apiClient;
    private final LittleSkinTokenStore tokenStore;
    private final LittleSkinSessionState sessionState = new LittleSkinSessionState();
    private final ScheduledExecutorService ioExecutor;
    private final AtomicInteger deviceFlowGeneration = new AtomicInteger();

    private volatile LittleSkinTokenStore.StoredSession storedSession;
    private volatile String activeDeviceCode = "";
    private volatile ScheduledFuture<?> pendingPollTask;

    public LittleSkinAccountService() {
        this(new LittleSkinApiClient(), new LittleSkinTokenStore());
    }

    LittleSkinAccountService(LittleSkinApiClient apiClient, LittleSkinTokenStore tokenStore) {
        this.apiClient = apiClient;
        this.tokenStore = tokenStore;
        this.ioExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, IO_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        loadStoredSessionMetadata();
        restoreStoredSessionOnStartup();
    }

    @Override
    public List<String> defaultScopes() {
        return DEFAULT_SCOPES;
    }

    @Override
    public LittleSkinAccountSnapshot snapshot() {
        return sessionState.snapshot();
    }

    @Override
    public void beginDeviceAuthorization() {
        LittleSkinConnectionState state = sessionState.snapshot().connectionState();
        if (state == LittleSkinConnectionState.REQUESTING_DEVICE_CODE
                || state == LittleSkinConnectionState.WAITING_USER_AUTH
                || state == LittleSkinConnectionState.POLLING_TOKEN
                || state == LittleSkinConnectionState.SYNCING
                || state == LittleSkinConnectionState.RESTORING_SESSION) {
            return;
        }
        activeDeviceCode = "";
        cancelPendingPollTask();
        int generation = deviceFlowGeneration.incrementAndGet();
        sessionState.beginRequestingDeviceCode();
        ioExecutor.execute(() -> requestDeviceAuthorization(generation));
    }

    @Override
    public void cancelDeviceAuthorization() {
        deviceFlowGeneration.incrementAndGet();
        activeDeviceCode = "";
        cancelPendingPollTask();
        sessionState.cancelDeviceAuthorization();
    }

    @Override
    public void refreshAccountSnapshot() {
        LittleSkinTokenStore.StoredSession current = storedSession;
        if (current == null) {
            sessionState.markError(
                    LittleSkinConnectionState.ERROR,
                    false,
                    false,
                    LittleSkinDeviceAuthSnapshot.inactive(),
                    "LittleSkin session is not available.",
                    "Start a new LittleSkin login to continue."
            );
            return;
        }
        List<String> scopes = current.grantedScopes().isEmpty() ? DEFAULT_SCOPES : current.grantedScopes();
        sessionState.beginSyncing(scopes, current.tokenExpiresAtEpochMillis(), "Refreshing LittleSkin closet...");
        ioExecutor.execute(() -> synchronizeStoredSession(current, false));
    }

    @Override
    public void logout() {
        deviceFlowGeneration.incrementAndGet();
        activeDeviceCode = "";
        cancelPendingPollTask();
        storedSession = null;
        tokenStore.clear();
        sessionState.clearForLogout();
    }

    @Override
    public void selectPlayer(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return;
        }
        sessionState.setSelectedPlayer(playerId);
        if (storedSession != null) {
            storedSession = storedSession.withSelectedPlayerId(playerId);
            tokenStore.save(storedSession);
        }
    }

    @Override
    public void applyTexture(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return;
        }
        logPendingImplementation("LS5", "Texture apply");
    }

    @Override
    public void applyClosetItem(String closetItemId) {
        if (closetItemId == null || closetItemId.isBlank()) {
            return;
        }
        logPendingImplementation("LS5", "Closet apply");
    }

    @Override
    public void clearError() {
        sessionState.clearError();
    }

    LittleSkinApiClient apiClient() {
        return apiClient;
    }

    LittleSkinTokenStore tokenStore() {
        return tokenStore;
    }

    ScheduledExecutorService ioExecutor() {
        return ioExecutor;
    }

    private void loadStoredSessionMetadata() {
        storedSession = tokenStore.load().orElse(null);
        sessionState.applyStoredSessionMetadata(storedSession);
    }

    private void restoreStoredSessionOnStartup() {
        LittleSkinTokenStore.StoredSession current = storedSession;
        if (current == null) {
            return;
        }
        sessionState.beginRestoringSession();
        ioExecutor.execute(() -> synchronizeStoredSession(current, true));
    }

    private void requestDeviceAuthorization(int generation) {
        try {
            LittleSkinApiClient.DeviceCodeResponse response = apiClient.requestDeviceCode(DEFAULT_SCOPES);
            if (!isGenerationCurrent(generation)) {
                return;
            }
            if (response.deviceCode().isBlank() || response.userCode().isBlank()) {
                throw new IOException("LittleSkin device code response is missing required fields.");
            }
            activeDeviceCode = response.deviceCode();
            LittleSkinDeviceAuthSnapshot snapshot = toDeviceAuthorizationSnapshot(response);
            dispatchToClient(() -> {
                if (!isGenerationCurrent(generation)) {
                    return;
                }
                sessionState.showDeviceAuthorization(
                        snapshot,
                        "Enter the LittleSkin code in your browser to continue."
                );
            });
            scheduleTokenPoll(generation, snapshot, snapshot.pollIntervalSeconds());
        } catch (Exception exception) {
            if (!isGenerationCurrent(generation)) {
                return;
            }
            LittleSkinDeviceAuthSnapshot inactive = LittleSkinDeviceAuthSnapshot.inactive();
            dispatchToClient(() -> sessionState.markError(
                    LittleSkinConnectionState.ERROR,
                    false,
                    storedSession != null,
                    inactive,
                    "LittleSkin device authorization could not start.",
                    describeException(exception)
            ));
        }
    }

    private void scheduleTokenPoll(int generation, LittleSkinDeviceAuthSnapshot snapshot, int intervalSeconds) {
        if (!isGenerationCurrent(generation)) {
            return;
        }
        cancelPendingPollTask();
        int safeInterval = Math.max(1, intervalSeconds);
        pendingPollTask = ioExecutor.schedule(
                () -> pollDeviceToken(generation, snapshot, safeInterval),
                safeInterval,
                TimeUnit.SECONDS
        );
    }

    private void pollDeviceToken(int generation, LittleSkinDeviceAuthSnapshot snapshot, int intervalSeconds) {
        pendingPollTask = null;
        if (!isGenerationCurrent(generation)) {
            return;
        }
        if (System.currentTimeMillis() >= snapshot.expiresAtEpochMillis()) {
            activeDeviceCode = "";
            dispatchToClient(() -> sessionState.markError(
                    LittleSkinConnectionState.ERROR,
                    false,
                    storedSession != null,
                    LittleSkinDeviceAuthSnapshot.inactive(),
                    "LittleSkin device code expired.",
                    "Start a new LittleSkin login to continue."
            ));
            return;
        }
        dispatchToClient(() -> {
            if (!isGenerationCurrent(generation)) {
                return;
            }
            sessionState.markPollingDeviceAuthorization(snapshot, "Checking LittleSkin authorization status...");
        });
        try {
            LittleSkinApiClient.OAuthTokenResponse tokenResponse = apiClient.exchangeDeviceToken(activeDeviceCode);
            if (!isGenerationCurrent(generation)) {
                return;
            }
            handleAuthorizedSession(
                    tokenResponse.toAuthorizedSession(null),
                    DEFAULT_SCOPES,
                    computeTokenExpiresAt(tokenResponse.expiresInSeconds()),
                    generation,
                    currentSelectedPlayerId()
            );
        } catch (LittleSkinApiClient.LittleSkinApiException exception) {
            if (!isGenerationCurrent(generation)) {
                return;
            }
            handlePollingException(generation, snapshot, intervalSeconds, exception);
        } catch (Exception exception) {
            if (!isGenerationCurrent(generation)) {
                return;
            }
            boolean authenticated = storedSession != null && !storedSession.accessToken().isBlank();
            LittleSkinDeviceAuthSnapshot errorSnapshot = authenticated
                    ? LittleSkinDeviceAuthSnapshot.inactive()
                    : snapshot;
            dispatchToClient(() -> sessionState.markError(
                    LittleSkinConnectionState.ERROR,
                    authenticated,
                    storedSession != null,
                    errorSnapshot,
                    "LittleSkin authorization polling failed.",
                    describeException(exception)
            ));
        }
    }

    private void handlePollingException(
            int generation,
            LittleSkinDeviceAuthSnapshot snapshot,
            int intervalSeconds,
            LittleSkinApiClient.LittleSkinApiException exception
    ) {
        String error = exception.error().toLowerCase(Locale.ROOT);
        switch (error) {
            case "authorization_pending" -> {
                dispatchToClient(() -> {
                    if (!isGenerationCurrent(generation)) {
                        return;
                    }
                    sessionState.showDeviceAuthorization(snapshot, "Waiting for LittleSkin authorization...");
                });
                scheduleTokenPoll(generation, snapshot, intervalSeconds);
            }
            case "slow_down" -> {
                int nextInterval = Math.max(intervalSeconds + 5, snapshot.pollIntervalSeconds() + 5);
                LittleSkinDeviceAuthSnapshot slowedSnapshot = new LittleSkinDeviceAuthSnapshot(
                        snapshot.active(),
                        snapshot.userCode(),
                        snapshot.verificationUri(),
                        snapshot.verificationUriComplete(),
                        snapshot.expiresAtEpochMillis(),
                        nextInterval
                );
                dispatchToClient(() -> {
                    if (!isGenerationCurrent(generation)) {
                        return;
                    }
                    sessionState.showDeviceAuthorization(
                            slowedSnapshot,
                            "LittleSkin asked the client to slow down before the next check."
                    );
                });
                scheduleTokenPoll(generation, slowedSnapshot, nextInterval);
            }
            case "access_denied" -> {
                activeDeviceCode = "";
                dispatchToClient(() -> sessionState.markError(
                        LittleSkinConnectionState.ERROR,
                        false,
                        storedSession != null,
                        LittleSkinDeviceAuthSnapshot.inactive(),
                        "LittleSkin authorization was denied.",
                        describeException(exception)
                ));
            }
            case "expired_token" -> {
                activeDeviceCode = "";
                dispatchToClient(() -> sessionState.markError(
                        LittleSkinConnectionState.ERROR,
                        false,
                        storedSession != null,
                        LittleSkinDeviceAuthSnapshot.inactive(),
                        "LittleSkin device code expired.",
                        "Start a new LittleSkin login to continue."
                ));
            }
            default -> dispatchToClient(() -> sessionState.markError(
                    LittleSkinConnectionState.ERROR,
                    false,
                    storedSession != null,
                    snapshot,
                    "LittleSkin authorization polling failed.",
                    describeException(exception)
            ));
        }
    }

    private void synchronizeStoredSession(LittleSkinTokenStore.StoredSession sessionToRestore, boolean startupRestore) {
        if (sessionToRestore == null || !sessionToRestore.equals(storedSession)) {
            return;
        }
        try {
            List<String> grantedScopes = sessionToRestore.grantedScopes().isEmpty()
                    ? DEFAULT_SCOPES
                    : sessionToRestore.grantedScopes();
            LittleSkinApiClient.AuthorizedSession session = new LittleSkinApiClient.AuthorizedSession(
                    sessionToRestore.accessToken(),
                    sessionToRestore.refreshToken()
            );
            long tokenExpiresAt = sessionToRestore.tokenExpiresAtEpochMillis();
            if (session.accessToken().isBlank() || shouldRefreshToken(tokenExpiresAt)) {
                if (!session.canRefresh()) {
                    throw new IOException("LittleSkin refresh token is missing.");
                }
                LittleSkinApiClient.OAuthTokenResponse refreshed = apiClient.refreshAccessToken(session.refreshToken());
                session = refreshed.toAuthorizedSession(session);
                tokenExpiresAt = computeTokenExpiresAt(refreshed.expiresInSeconds());
                storeSession(createStoredSession(
                        session,
                        grantedScopes,
                        tokenExpiresAt,
                        sessionToRestore.selectedPlayerId(),
                        sessionToRestore.lastSuccessfulSyncEpochMillis()
                ));
            }
            long effectiveTokenExpiresAt = tokenExpiresAt;
            dispatchToClient(() -> sessionState.beginSyncing(
                    grantedScopes,
                    effectiveTokenExpiresAt,
                    "Syncing LittleSkin closet..."
            ));
            syncCloset(session, grantedScopes, effectiveTokenExpiresAt, sessionToRestore.selectedPlayerId());
        } catch (LittleSkinApiClient.LittleSkinApiException exception) {
            if (startupRestore && isInvalidGrant(exception)) {
                clearStoredSessionWithError(
                        "Stored LittleSkin session could not be restored.",
                        describeException(exception)
                );
                return;
            }
            dispatchToClient(() -> sessionState.markError(
                    LittleSkinConnectionState.ERROR,
                    true,
                    storedSession != null,
                    LittleSkinDeviceAuthSnapshot.inactive(),
                    "LittleSkin closet sync failed.",
                    describeException(exception)
            ));
        } catch (Exception exception) {
            if (startupRestore) {
                clearStoredSessionWithError(
                        "Stored LittleSkin session could not be restored.",
                        describeException(exception)
                );
                return;
            }
            dispatchToClient(() -> sessionState.markError(
                    LittleSkinConnectionState.ERROR,
                    true,
                    storedSession != null,
                    LittleSkinDeviceAuthSnapshot.inactive(),
                    "LittleSkin closet sync failed.",
                    describeException(exception)
            ));
        }
    }

    private void handleAuthorizedSession(
            LittleSkinApiClient.AuthorizedSession session,
            List<String> scopes,
            long tokenExpiresAt,
            int generation,
            String selectedPlayerId
    ) throws IOException {
        if (!isGenerationCurrent(generation)) {
            return;
        }
        activeDeviceCode = "";
        storeSession(createStoredSession(session, scopes, tokenExpiresAt, selectedPlayerId, 0L));
        dispatchToClient(() -> sessionState.beginSyncing(scopes, tokenExpiresAt, "LittleSkin authorization succeeded. Syncing closet..."));
        syncCloset(session, scopes, tokenExpiresAt, selectedPlayerId);
    }

    private void syncCloset(
            LittleSkinApiClient.AuthorizedSession session,
            List<String> scopes,
            long tokenExpiresAt,
            String selectedPlayerId
    ) throws IOException {
        LittleSkinApiClient.AuthorizedResult<LittleSkinApiClient.ClosetPageResource> result =
                apiClient.listClosetItems(session, DEFAULT_CLOSET_QUERY);
        long syncTime = System.currentTimeMillis();
        LittleSkinTokenStore.StoredSession updatedSession = createStoredSession(
                result.session(),
                scopes,
                tokenExpiresAt,
                selectedPlayerId,
                syncTime
        );
        List<LittleSkinClosetItemSummary> closetItems = mapClosetItems(result.body());
        storeSession(updatedSession);
        activeDeviceCode = "";
        dispatchToClient(() -> sessionState.applyClosetReady(
                updatedSession,
                closetItems,
                "LittleSkin closet synced."
        ));
    }

    private List<LittleSkinClosetItemSummary> mapClosetItems(LittleSkinApiClient.ClosetPageResource resource) {
        if (resource == null || resource.data().isEmpty()) {
            return List.of();
        }
        return resource.data().stream()
                .map(item -> new LittleSkinClosetItemSummary(
                        Integer.toString(item.tid()),
                        Integer.toString(item.tid()),
                        item.itemName().isBlank() ? item.name() : item.itemName(),
                        item.type(),
                        ""
                ))
                .toList();
    }

    private LittleSkinTokenStore.StoredSession createStoredSession(
            LittleSkinApiClient.AuthorizedSession session,
            List<String> scopes,
            long tokenExpiresAt,
            String selectedPlayerId,
            long lastSyncEpochMillis
    ) {
        return new LittleSkinTokenStore.StoredSession(
                LittleSkinTokenStore.StoredSession.CURRENT_SCHEMA_VERSION,
                session.accessToken(),
                session.refreshToken(),
                tokenExpiresAt,
                scopes,
                selectedPlayerId == null ? "" : selectedPlayerId,
                lastSyncEpochMillis
        );
    }

    private LittleSkinDeviceAuthSnapshot toDeviceAuthorizationSnapshot(LittleSkinApiClient.DeviceCodeResponse response) {
        return new LittleSkinDeviceAuthSnapshot(
                true,
                response.userCode(),
                response.verificationUri(),
                response.verificationUriComplete(),
                System.currentTimeMillis() + Math.max(1L, response.expiresInSeconds()) * 1000L,
                Math.max(1, response.interval())
        );
    }

    private long computeTokenExpiresAt(long expiresInSeconds) {
        return System.currentTimeMillis() + Math.max(1L, expiresInSeconds) * 1000L;
    }

    private boolean shouldRefreshToken(long tokenExpiresAtEpochMillis) {
        return tokenExpiresAtEpochMillis <= 0L
                || System.currentTimeMillis() >= tokenExpiresAtEpochMillis - TOKEN_EXPIRY_SAFETY_WINDOW_MILLIS;
    }

    private boolean isInvalidGrant(LittleSkinApiClient.LittleSkinApiException exception) {
        return "invalid_grant".equalsIgnoreCase(exception.error());
    }

    private String currentSelectedPlayerId() {
        LittleSkinTokenStore.StoredSession current = storedSession;
        return current == null ? "" : current.selectedPlayerId();
    }

    private void clearStoredSessionWithError(String status, String error) {
        storedSession = null;
        tokenStore.clear();
        activeDeviceCode = "";
        dispatchToClient(() -> {
            sessionState.applyStoredSessionMetadata(null);
            sessionState.markError(
                    LittleSkinConnectionState.ERROR,
                    false,
                    false,
                    LittleSkinDeviceAuthSnapshot.inactive(),
                    status,
                    error
            );
        });
    }

    private void storeSession(LittleSkinTokenStore.StoredSession updatedSession) {
        storedSession = updatedSession;
        tokenStore.save(updatedSession);
    }

    private boolean isGenerationCurrent(int generation) {
        return generation == deviceFlowGeneration.get();
    }

    private void cancelPendingPollTask() {
        ScheduledFuture<?> task = pendingPollTask;
        pendingPollTask = null;
        if (task != null) {
            task.cancel(false);
        }
    }

    private void dispatchToClient(Runnable runnable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            runnable.run();
            return;
        }
        minecraft.execute(runnable);
    }

    private String describeException(Exception exception) {
        if (exception instanceof LittleSkinApiClient.LittleSkinApiException apiException) {
            if (!apiException.errorDescription().isBlank()) {
                return apiException.errorDescription();
            }
            if (!apiException.error().isBlank()) {
                return apiException.error();
            }
            if (!apiException.apiMessage().isBlank()) {
                return apiException.apiMessage();
            }
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void logPendingImplementation(String milestone, String action) {
        String normalizedAction = action.toLowerCase(Locale.ROOT);
        LTSXAssistant.LOGGER.info("[ltsxassistant] LittleSkin {} is pending {}.", normalizedAction, milestone);
    }
}
