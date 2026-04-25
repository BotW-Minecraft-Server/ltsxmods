package link.botwmcs.ltsxassistant.service.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Persistent storage for LittleSkin session metadata.
 */
final class LittleSkinTokenStore {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final Path storagePath;

    LittleSkinTokenStore() {
        this(defaultStoragePath());
    }

    LittleSkinTokenStore(Path storagePath) {
        this.storagePath = storagePath;
    }

    Path storagePath() {
        return storagePath;
    }

    Optional<StoredSession> load() {
        if (!Files.isRegularFile(storagePath)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(storagePath, StandardCharsets.UTF_8)) {
            StoredSession storedSession = GSON.fromJson(reader, StoredSession.class);
            return Optional.ofNullable(storedSession);
        } catch (IOException | JsonParseException exception) {
            LTSXAssistant.LOGGER.warn("[ltsxassistant] Failed to load LittleSkin session metadata from {}.", storagePath, exception);
            return Optional.empty();
        }
    }

    void save(StoredSession storedSession) {
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(storagePath, StandardCharsets.UTF_8)) {
                GSON.toJson(storedSession, writer);
            }
        } catch (IOException exception) {
            LTSXAssistant.LOGGER.warn("[ltsxassistant] Failed to save LittleSkin session metadata to {}.", storagePath, exception);
        }
    }

    void clear() {
        try {
            Files.deleteIfExists(storagePath);
        } catch (IOException exception) {
            LTSXAssistant.LOGGER.warn("[ltsxassistant] Failed to clear LittleSkin session metadata at {}.", storagePath, exception);
        }
    }

    private static Path defaultStoragePath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(LTSXAssistant.MODID)
                .resolve("littleskin-account.json");
    }

    record StoredSession(
            int schemaVersion,
            String accessToken,
            String refreshToken,
            long tokenExpiresAtEpochMillis,
            List<String> grantedScopes,
            String selectedPlayerId,
            long lastSuccessfulSyncEpochMillis
    ) {
        static final int CURRENT_SCHEMA_VERSION = 1;

        public StoredSession {
            accessToken = accessToken == null ? "" : accessToken;
            refreshToken = refreshToken == null ? "" : refreshToken;
            grantedScopes = grantedScopes == null ? List.of() : List.copyOf(grantedScopes);
            selectedPlayerId = selectedPlayerId == null ? "" : selectedPlayerId;
        }

        StoredSession withSelectedPlayerId(String selectedPlayerId) {
            return new StoredSession(
                    schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion,
                    accessToken,
                    refreshToken,
                    tokenExpiresAtEpochMillis,
                    grantedScopes,
                    selectedPlayerId,
                    lastSuccessfulSyncEpochMillis
            );
        }
    }
}
