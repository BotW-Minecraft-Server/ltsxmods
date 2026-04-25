package link.botwmcs.ltsxassistant.service.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Central LittleSkin HTTP entry point. All REST calls should stay inside this class.
 */
final class LittleSkinApiClient {
    private static final String OPEN_API_BASE_URL = "https://open.littleskin.cn/";
    private static final String SITE_API_BASE_URL = "https://littleskin.cn/";
    private static final String DEFAULT_USER_AGENT = "LTSXAssistant-LittleSkin/LS1";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private static final String OAUTH_DEVICE_CODE_PATH = "oauth/device_code";
    private static final String OAUTH_TOKEN_PATH = "oauth/token";
    private static final String API_USER_PATH = "api/user";
    private static final String API_PLAYERS_PATH = "api/players";
    private static final String API_CLOSET_PATH = "api/closet";
    private static final String API_PREMIUM_VERIFICATION_PATH = "api/premium-verification";
    private static final String API_YGGDRASIL_PROFILES_PATH = "api/yggdrasil/sessionserver/session/minecraft/profile";
    private static final String API_MINECRAFT_TOKEN_PATH = "api/yggdrasil/authserver/oauth";

    private static final Type PLAYER_LIST_TYPE = new TypeToken<List<PlayerResource>>() {
    }.getType();
    private static final Type CLOSET_PAGE_TYPE = new TypeToken<ClosetPageResource>() {
    }.getType();
    private static final Type YGGDRASIL_PROFILE_LIST_TYPE = new TypeToken<List<YggdrasilProfileResource>>() {
    }.getType();

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Settings settings;

    LittleSkinApiClient() {
        this(
                new OkHttpClient.Builder()
                        .retryOnConnectionFailure(true)
                        .build(),
                new GsonBuilder()
                        .disableHtmlEscaping()
                        .create(),
                Settings.defaults()
        );
    }

    LittleSkinApiClient(OkHttpClient httpClient, Gson gson, Settings settings) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    DeviceCodeResponse requestDeviceCode(List<String> scopes) throws IOException {
        FormBody.Builder bodyBuilder = new FormBody.Builder()
                .add("client_id", requireClientId());
        String scope = String.join(" ", scopes == null ? List.of() : scopes);
        if (!scope.isBlank()) {
            bodyBuilder.add("scope", scope);
        }
        return executeJson(
                buildRequest("POST", resolveOpenPath(OAUTH_DEVICE_CODE_PATH), bodyBuilder.build(), null),
                json -> fromJson(json, DeviceCodeResponse.class, "device_code response")
        );
    }

    OAuthTokenResponse exchangeDeviceToken(String deviceCode) throws IOException {
        FormBody body = new FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", requireClientId())
                .add("device_code", deviceCode == null ? "" : deviceCode)
                .build();
        return executeJson(
                buildRequest("POST", resolveOpenPath(OAUTH_TOKEN_PATH), body, null),
                json -> fromJson(json, OAuthTokenResponse.class, "device token response")
        );
    }

    OAuthTokenResponse refreshAccessToken(String refreshToken) throws IOException {
        FormBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken == null ? "" : refreshToken)
                .add("client_id", requireClientId())
                .build();
        return executeJson(
                buildRequest("POST", resolveOpenPath(OAUTH_TOKEN_PATH), body, null),
                json -> fromJson(json, OAuthTokenResponse.class, "refresh token response")
        );
    }

    AuthorizedResult<UserResource> getCurrentUser(AuthorizedSession session) throws IOException {
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("GET", resolveSitePath(API_USER_PATH), null, accessToken),
                json -> fromJson(json, UserResource.class, "user response")
        );
    }

    AuthorizedResult<List<PlayerResource>> listPlayers(AuthorizedSession session) throws IOException {
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("GET", resolveSitePath(API_PLAYERS_PATH), null, accessToken),
                json -> fromJson(json, PLAYER_LIST_TYPE, "player list response")
        );
    }

    AuthorizedResult<PlayerResource> getPlayer(AuthorizedSession session, int playerId) throws IOException {
        AuthorizedResult<List<PlayerResource>> playersResult = listPlayers(session);
        for (PlayerResource player : playersResult.body()) {
            if (player != null && player.pid() == playerId) {
                return new AuthorizedResult<>(player, playersResult.session(), playersResult.requestId());
            }
        }
        throw LittleSkinApiException.notFound(
                "Player " + playerId + " was not found in current user player list.",
                playersResult.requestId()
        );
    }

    AuthorizedResult<ApiEnvelope<PlayerResource>> createPlayer(AuthorizedSession session, String name) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name == null ? "" : name);
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("POST", resolveSitePath(API_PLAYERS_PATH), jsonRequestBody(body), accessToken),
                json -> parseApiEnvelope(json, PlayerResource.class, "create player response")
        );
    }

    AuthorizedResult<ApiMessage> deletePlayer(AuthorizedSession session, int playerId) throws IOException {
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("DELETE", resolveSitePath(API_PLAYERS_PATH + "/" + playerId), null, accessToken),
                json -> parseApiMessage(json, "delete player response")
        );
    }

    AuthorizedResult<ApiEnvelope<PlayerResource>> renamePlayer(
            AuthorizedSession session,
            int playerId,
            String newName
    ) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", newName == null ? "" : newName);
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest(
                        "PUT",
                        resolveSitePath(API_PLAYERS_PATH + "/" + playerId + "/name"),
                        jsonRequestBody(body),
                        accessToken
                ),
                json -> parseApiEnvelope(json, PlayerResource.class, "rename player response")
        );
    }

    AuthorizedResult<ApiEnvelope<PlayerResource>> setPlayerTextures(
            AuthorizedSession session,
            int playerId,
            @Nullable Integer skinTextureId,
            @Nullable Integer capeTextureId
    ) throws IOException {
        JsonObject body = new JsonObject();
        if (skinTextureId != null) {
            body.addProperty("skin", skinTextureId);
        }
        if (capeTextureId != null) {
            body.addProperty("cape", capeTextureId);
        }
        if (body.size() == 0) {
            throw new IllegalArgumentException("At least one texture id must be provided.");
        }
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest(
                        "PUT",
                        resolveSitePath(API_PLAYERS_PATH + "/" + playerId + "/textures"),
                        jsonRequestBody(body),
                        accessToken
                ),
                json -> parseApiEnvelope(json, PlayerResource.class, "set player textures response")
        );
    }

    AuthorizedResult<ApiEnvelope<PlayerResource>> clearPlayerTextures(
            AuthorizedSession session,
            int playerId,
            boolean clearSkin,
            boolean clearCape
    ) throws IOException {
        if (!clearSkin && !clearCape) {
            throw new IllegalArgumentException("At least one texture slot must be cleared.");
        }
        HttpUrl.Builder urlBuilder = resolveSitePath(API_PLAYERS_PATH + "/" + playerId + "/textures").newBuilder();
        if (clearSkin) {
            urlBuilder.addQueryParameter("type[]", "skin");
        }
        if (clearCape) {
            urlBuilder.addQueryParameter("type[]", "cape");
        }
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("DELETE", urlBuilder.build(), null, accessToken),
                json -> parseApiEnvelope(json, PlayerResource.class, "clear player textures response")
        );
    }

    AuthorizedResult<ClosetPageResource> listClosetItems(
            AuthorizedSession session,
            @Nullable ClosetQuery query
    ) throws IOException {
        ClosetQuery normalized = query == null ? ClosetQuery.defaults() : query.normalized();
        HttpUrl.Builder urlBuilder = resolveSitePath(API_CLOSET_PATH).newBuilder()
                .addQueryParameter("category", normalized.category());
        if (!normalized.search().isBlank()) {
            urlBuilder.addQueryParameter("q", normalized.search());
        }
        if (normalized.perPage() > 0) {
            urlBuilder.addQueryParameter("perPage", Integer.toString(normalized.perPage()));
        }
        if (normalized.page() > 0) {
            urlBuilder.addQueryParameter("page", Integer.toString(normalized.page()));
        }
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("GET", urlBuilder.build(), null, accessToken),
                json -> fromJson(json, CLOSET_PAGE_TYPE, "closet page response")
        );
    }

    AuthorizedResult<ApiMessage> addClosetItem(
            AuthorizedSession session,
            int textureId,
            String displayName
    ) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("tid", textureId);
        body.addProperty("name", displayName == null ? "" : displayName);
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("POST", resolveSitePath(API_CLOSET_PATH), jsonRequestBody(body), accessToken),
                json -> parseApiMessage(json, "add closet item response")
        );
    }

    AuthorizedResult<ApiMessage> renameClosetItem(
            AuthorizedSession session,
            int textureId,
            String displayName
    ) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", displayName == null ? "" : displayName);
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest(
                        "PUT",
                        resolveSitePath(API_CLOSET_PATH + "/" + textureId),
                        jsonRequestBody(body),
                        accessToken
                ),
                json -> parseApiMessage(json, "rename closet item response")
        );
    }

    AuthorizedResult<ApiMessage> removeClosetItem(AuthorizedSession session, int textureId) throws IOException {
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("DELETE", resolveSitePath(API_CLOSET_PATH + "/" + textureId), null, accessToken),
                json -> parseApiMessage(json, "remove closet item response")
        );
    }

    AuthorizedResult<PremiumVerificationResource> getPremiumVerification(AuthorizedSession session) throws IOException {
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("GET", resolveSitePath(API_PREMIUM_VERIFICATION_PATH), null, accessToken),
                json -> {
                    PremiumVerificationResource resource = fromJson(
                            json,
                            PremiumVerificationResource.class,
                            "premium verification response"
                    );
                    if (resource.code() != 0) {
                        throw LittleSkinApiException.business(
                                200,
                                "",
                                resource.code(),
                                "Premium verification failed.",
                                null,
                                gson.toJson(json)
                        );
                    }
                    return resource;
                }
        );
    }

    AuthorizedResult<List<YggdrasilProfileResource>> listYggdrasilProfiles(AuthorizedSession session) throws IOException {
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest("GET", resolveSitePath(API_YGGDRASIL_PROFILES_PATH), null, accessToken),
                json -> fromJson(json, YGGDRASIL_PROFILE_LIST_TYPE, "yggdrasil profile list response")
        );
    }

    AuthorizedResult<MinecraftTokenResponse> createMinecraftToken(
            AuthorizedSession session,
            String selectedPlayerUuid
    ) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", selectedPlayerUuid == null ? "" : selectedPlayerUuid);
        return executeAuthorizedJson(
                session,
                accessToken -> buildRequest(
                        "POST",
                        resolveSitePath(API_MINECRAFT_TOKEN_PATH),
                        jsonRequestBody(body),
                        accessToken
                ),
                json -> fromJson(json, MinecraftTokenResponse.class, "minecraft token response")
        );
    }

    Settings settings() {
        return settings;
    }

    private RequestBody jsonRequestBody(JsonElement json) {
        return RequestBody.create(gson.toJson(json), JSON_MEDIA_TYPE);
    }

    private Request buildRequest(
            String method,
            HttpUrl url,
            @Nullable RequestBody body,
            @Nullable String accessToken
    ) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", settings.userAgent());
        if (accessToken != null && !accessToken.isBlank()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        switch (method) {
            case "GET" -> builder.get();
            case "POST" -> builder.post(requireBody(method, body));
            case "PUT" -> builder.put(requireBody(method, body));
            case "PATCH" -> builder.patch(requireBody(method, body));
            case "DELETE" -> {
                if (body == null) {
                    builder.delete();
                } else {
                    builder.delete(body);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        return builder.build();
    }

    private RequestBody requireBody(String method, @Nullable RequestBody body) {
        if (body == null) {
            throw new IllegalArgumentException("HTTP " + method + " requires a request body.");
        }
        return body;
    }

    private HttpUrl resolveOpenPath(String path) {
        return resolve(settings.openApiBaseUrl(), path);
    }

    private HttpUrl resolveSitePath(String path) {
        return resolve(settings.siteBaseUrl(), path);
    }

    private HttpUrl resolve(HttpUrl baseUrl, String path) {
        String normalized = path == null ? "" : path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        HttpUrl.Builder builder = baseUrl.newBuilder();
        if (!normalized.isBlank()) {
            builder.addEncodedPathSegments(normalized);
        }
        return builder.build();
    }

    private String requireClientId() {
        if (settings.clientId().isBlank()) {
            throw new IllegalStateException("LittleSkin clientId is not configured.");
        }
        return settings.clientId();
    }

    private <T> T executeJson(Request request, JsonBodyParser<T> parser) throws IOException {
        return parser.apply(executeForJson(request).json());
    }

    private <T> AuthorizedResult<T> executeAuthorizedJson(
            AuthorizedSession session,
            Function<String, Request> requestFactory,
            JsonBodyParser<T> parser
    ) throws IOException {
        Objects.requireNonNull(session, "session");
        return executeAuthorizedJson(session, requestFactory, parser, false);
    }

    private <T> AuthorizedResult<T> executeAuthorizedJson(
            AuthorizedSession session,
            Function<String, Request> requestFactory,
            JsonBodyParser<T> parser,
            boolean retried
    ) throws IOException {
        RawJsonResponse rawResponse;
        try {
            rawResponse = executeForJson(requestFactory.apply(session.accessToken()));
        } catch (LittleSkinApiException exception) {
            if (exception.statusCode() == 401 && !retried && session.canRefresh()) {
                OAuthTokenResponse refreshed = refreshAccessToken(session.refreshToken());
                AuthorizedSession refreshedSession = refreshed.toAuthorizedSession(session);
                return executeAuthorizedJson(refreshedSession, requestFactory, parser, true);
            }
            throw exception;
        }
        return new AuthorizedResult<>(
                parser.apply(rawResponse.json()),
                session,
                rawResponse.requestId()
        );
    }

    private RawJsonResponse executeForJson(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String requestId = response.header(REQUEST_ID_HEADER, "");
            String body = readResponseBody(response.body());
            JsonElement json = parseJson(body);
            if (!response.isSuccessful()) {
                throw buildApiException(response.code(), requestId, body, json);
            }
            return new RawJsonResponse(response.code(), requestId, body, json);
        }
    }

    private String readResponseBody(@Nullable ResponseBody responseBody) throws IOException {
        return responseBody == null ? "" : responseBody.string();
    }

    private JsonElement parseJson(String body) throws LittleSkinApiException {
        if (body == null || body.isBlank()) {
            return JsonNull.INSTANCE;
        }
        try {
            return JsonParser.parseString(body);
        } catch (JsonParseException exception) {
            throw LittleSkinApiException.parse(
                    "Response was not valid JSON.",
                    body,
                    exception
            );
        }
    }

    private LittleSkinApiException buildApiException(
            int statusCode,
            String requestId,
            String rawBody,
            JsonElement json
    ) {
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("error")) {
                String error = stringValue(object, "error");
                String description = stringValue(object, "error_description");
                return LittleSkinApiException.oauth(statusCode, requestId, error, description, rawBody);
            }
            if (object.has("code")) {
                return LittleSkinApiException.business(
                        statusCode,
                        requestId,
                        intValue(object, "code"),
                        stringValue(object, "message"),
                        null,
                        rawBody
                );
            }
        }
        return LittleSkinApiException.http(statusCode, requestId, rawBody);
    }

    private <T> T fromJson(JsonElement json, Class<T> type, String context) throws LittleSkinApiException {
        return fromJson(json, (Type) type, context);
    }

    private <T> T fromJson(JsonElement json, Type type, String context) throws LittleSkinApiException {
        try {
            T parsed = gson.fromJson(json, type);
            if (parsed == null) {
                throw LittleSkinApiException.parse(context + " parsed as null.", gson.toJson(json), null);
            }
            return parsed;
        } catch (JsonParseException exception) {
            throw LittleSkinApiException.parse(context + " could not be parsed.", gson.toJson(json), exception);
        }
    }

    private <T> ApiEnvelope<T> parseApiEnvelope(JsonElement json, Class<T> dataType, String context) throws LittleSkinApiException {
        Type envelopeType = TypeToken.getParameterized(ApiEnvelope.class, dataType).getType();
        ApiEnvelope<T> envelope = fromJson(json, envelopeType, context);
        if (envelope.code() != 0) {
            throw LittleSkinApiException.business(
                    200,
                    "",
                    envelope.code(),
                    envelope.message(),
                    null,
                    gson.toJson(json)
            );
        }
        return envelope;
    }

    private ApiMessage parseApiMessage(JsonElement json, String context) throws LittleSkinApiException {
        ApiMessage message = fromJson(json, ApiMessage.class, context);
        if (message.code() != 0) {
            throw LittleSkinApiException.business(
                    200,
                    "",
                    message.code(),
                    message.message(),
                    null,
                    gson.toJson(json)
            );
        }
        return message;
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static int intValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsInt();
    }

    record Settings(
            String clientId,
            HttpUrl openApiBaseUrl,
            HttpUrl siteBaseUrl,
            String userAgent
    ) {
        Settings {
            clientId = clientId == null ? "" : clientId;
            openApiBaseUrl = Objects.requireNonNull(openApiBaseUrl, "openApiBaseUrl");
            siteBaseUrl = Objects.requireNonNull(siteBaseUrl, "siteBaseUrl");
            userAgent = userAgent == null || userAgent.isBlank() ? DEFAULT_USER_AGENT : userAgent;
        }

        static Settings defaults() {
            return new Settings(
                    LittleSkinOAuthConstants.CLIENT_ID,
                    HttpUrl.get(OPEN_API_BASE_URL),
                    HttpUrl.get(SITE_API_BASE_URL),
                    DEFAULT_USER_AGENT
            );
        }

        Settings withClientId(String clientId) {
            return new Settings(clientId, openApiBaseUrl, siteBaseUrl, userAgent);
        }
    }

    record AuthorizedSession(String accessToken, String refreshToken) {
        AuthorizedSession {
            accessToken = accessToken == null ? "" : accessToken;
            refreshToken = refreshToken == null ? "" : refreshToken;
        }

        boolean canRefresh() {
            return !refreshToken.isBlank();
        }
    }

    record AuthorizedResult<T>(T body, AuthorizedSession session, String requestId) {
        AuthorizedResult {
            session = Objects.requireNonNull(session, "session");
            requestId = requestId == null ? "" : requestId;
        }
    }

    record RawJsonResponse(int statusCode, String requestId, String rawBody, JsonElement json) {
        RawJsonResponse {
            requestId = requestId == null ? "" : requestId;
            rawBody = rawBody == null ? "" : rawBody;
            json = json == null ? JsonNull.INSTANCE : json;
        }
    }

    record DeviceCodeResponse(
            @SerializedName("device_code") String deviceCode,
            @SerializedName("user_code") String userCode,
            @SerializedName("verification_uri") String verificationUri,
            @SerializedName("verification_uri_complete") String verificationUriComplete,
            @SerializedName("expires_in") long expiresInSeconds,
            int interval
    ) {
        public DeviceCodeResponse {
            deviceCode = deviceCode == null ? "" : deviceCode;
            userCode = userCode == null ? "" : userCode;
            verificationUri = verificationUri == null ? "" : verificationUri;
            verificationUriComplete = verificationUriComplete == null ? "" : verificationUriComplete;
        }
    }

    record OAuthTokenResponse(
            @SerializedName("token_type") String tokenType,
            @SerializedName("expires_in") long expiresInSeconds,
            @SerializedName("access_token") String accessToken,
            @SerializedName("refresh_token") String refreshToken,
            @SerializedName("id_token") String idToken
    ) {
        public OAuthTokenResponse {
            tokenType = tokenType == null ? "" : tokenType;
            accessToken = accessToken == null ? "" : accessToken;
            refreshToken = refreshToken == null ? "" : refreshToken;
            idToken = idToken == null ? "" : idToken;
        }

        AuthorizedSession toAuthorizedSession(@Nullable AuthorizedSession previousSession) {
            String effectiveRefreshToken = !refreshToken.isBlank()
                    ? refreshToken
                    : previousSession == null ? "" : previousSession.refreshToken();
            return new AuthorizedSession(accessToken, effectiveRefreshToken);
        }
    }

    record UserResource(
            int uid,
            String email,
            String nickname,
            @SerializedName("player_name") String playerName,
            int avatar,
            int score,
            int permission,
            boolean verified
    ) {
        public UserResource {
            email = email == null ? "" : email;
            nickname = nickname == null ? "" : nickname;
            playerName = playerName == null ? "" : playerName;
        }
    }

    record PlayerResource(
            int pid,
            int uid,
            String name,
            @SerializedName("tid_skin") int skinTextureId,
            @SerializedName("tid_cape") int capeTextureId,
            @SerializedName("last_modified") String lastModified
    ) {
        public PlayerResource {
            name = name == null ? "" : name;
            lastModified = lastModified == null ? "" : lastModified;
        }
    }

    record ApiEnvelope<T>(int code, String message, T data) {
        public ApiEnvelope {
            message = message == null ? "" : message;
        }
    }

    record ApiMessage(int code, String message) {
        public ApiMessage {
            message = message == null ? "" : message;
        }
    }

    record ClosetQuery(String category, String search, int perPage, int page) {
        ClosetQuery normalized() {
            String normalizedCategory = "cape".equalsIgnoreCase(category) ? "cape" : "skin";
            String normalizedSearch = search == null ? "" : search;
            return new ClosetQuery(
                    normalizedCategory,
                    normalizedSearch,
                    Math.max(perPage, 0),
                    Math.max(page, 0)
            );
        }

        static ClosetQuery defaults() {
            return new ClosetQuery("skin", "", 0, 0);
        }
    }

    record ClosetPageResource(
            @SerializedName("current_page") int currentPage,
            List<ClosetItemResource> data,
            @SerializedName("per_page") int perPage,
            @SerializedName("last_page") int lastPage,
            int total
    ) {
        public ClosetPageResource {
            data = data == null ? List.of() : List.copyOf(data);
        }
    }

    record ClosetItemResource(
            int tid,
            String name,
            String type,
            String hash,
            int size,
            int uploader,
            @SerializedName("public") boolean publicTexture,
            int likes,
            @SerializedName("upload_at") String uploadAt,
            PivotResource pivot
    ) {
        public ClosetItemResource {
            name = name == null ? "" : name;
            type = type == null ? "" : type;
            hash = hash == null ? "" : hash;
            uploadAt = uploadAt == null ? "" : uploadAt;
        }

        String itemName() {
            return pivot == null ? "" : pivot.itemName();
        }
    }

    record PivotResource(@SerializedName("item_name") String itemName) {
        public PivotResource {
            itemName = itemName == null ? "" : itemName;
        }
    }

    record PremiumVerificationResource(int code, boolean verified, String uuid) {
        public PremiumVerificationResource {
            uuid = uuid == null ? "" : uuid;
        }
    }

    record YggdrasilProfileResource(String id, String name, List<YggdrasilPropertyResource> properties) {
        public YggdrasilProfileResource {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            properties = properties == null ? List.of() : List.copyOf(properties);
        }
    }

    record YggdrasilPropertyResource(String name, String value) {
        public YggdrasilPropertyResource {
            name = name == null ? "" : name;
            value = value == null ? "" : value;
        }
    }

    record MinecraftTokenResponse(
            @SerializedName("accessToken") String accessToken,
            @SerializedName("clientToken") String clientToken,
            @SerializedName("availableProfiles") List<BasicYggdrasilProfile> availableProfiles,
            @SerializedName("selectedProfile") BasicYggdrasilProfile selectedProfile
    ) {
        public MinecraftTokenResponse {
            accessToken = accessToken == null ? "" : accessToken;
            clientToken = clientToken == null ? "" : clientToken;
            availableProfiles = availableProfiles == null ? List.of() : List.copyOf(availableProfiles);
        }
    }

    record BasicYggdrasilProfile(String id, String name) {
        public BasicYggdrasilProfile {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
        }
    }

    @FunctionalInterface
    private interface JsonBodyParser<T> {
        T apply(JsonElement json) throws IOException;
    }

    static final class LittleSkinApiException extends IOException {
        private final int statusCode;
        private final String requestId;
        private final String error;
        private final String errorDescription;
        private final int apiCode;
        private final String apiMessage;
        private final String rawBody;

        private LittleSkinApiException(
                String message,
                int statusCode,
                String requestId,
                String error,
                String errorDescription,
                int apiCode,
                String apiMessage,
                String rawBody,
                @Nullable Throwable cause
        ) {
            super(message, cause);
            this.statusCode = statusCode;
            this.requestId = requestId == null ? "" : requestId;
            this.error = error == null ? "" : error;
            this.errorDescription = errorDescription == null ? "" : errorDescription;
            this.apiCode = apiCode;
            this.apiMessage = apiMessage == null ? "" : apiMessage;
            this.rawBody = rawBody == null ? "" : rawBody;
        }

        static LittleSkinApiException http(int statusCode, String requestId, String rawBody) {
            return new LittleSkinApiException(
                    "LittleSkin API request failed with HTTP " + statusCode + ".",
                    statusCode,
                    requestId,
                    "",
                    "",
                    0,
                    "",
                    rawBody,
                    null
            );
        }

        static LittleSkinApiException oauth(
                int statusCode,
                String requestId,
                String error,
                String errorDescription,
                String rawBody
        ) {
            String detail = errorDescription == null || errorDescription.isBlank()
                    ? error
                    : error + ": " + errorDescription;
            return new LittleSkinApiException(
                    "LittleSkin OAuth request failed: " + detail,
                    statusCode,
                    requestId,
                    error,
                    errorDescription,
                    0,
                    "",
                    rawBody,
                    null
            );
        }

        static LittleSkinApiException business(
                int statusCode,
                String requestId,
                int apiCode,
                String apiMessage,
                @Nullable String errorDescription,
                String rawBody
        ) {
            return new LittleSkinApiException(
                    "LittleSkin API business error " + apiCode + ": " + (apiMessage == null ? "" : apiMessage),
                    statusCode,
                    requestId,
                    "",
                    errorDescription == null ? "" : errorDescription,
                    apiCode,
                    apiMessage,
                    rawBody,
                    null
            );
        }

        static LittleSkinApiException parse(String message, String rawBody, @Nullable Throwable cause) {
            return new LittleSkinApiException(message, 0, "", "", "", 0, "", rawBody, cause);
        }

        static LittleSkinApiException notFound(String message, String requestId) {
            return new LittleSkinApiException(message, 404, requestId, "", "", 0, "", "", null);
        }

        int statusCode() {
            return statusCode;
        }

        String requestId() {
            return requestId;
        }

        String error() {
            return error;
        }

        String errorDescription() {
            return errorDescription;
        }

        int apiCode() {
            return apiCode;
        }

        String apiMessage() {
            return apiMessage;
        }

        String rawBody() {
            return rawBody;
        }
    }
}
