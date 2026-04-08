package link.botwmcs.ltsxassistant.service.soundengine;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import link.botwmcs.ltsxassistant.Config;
import link.botwmcs.ltsxassistant.api.soundengine.MusicAlbumApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicAlbumDescriptor;
import link.botwmcs.ltsxassistant.api.soundengine.MusicCoverApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicEngineMode;
import link.botwmcs.ltsxassistant.api.soundengine.MusicPlaybackApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicSceneApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicTrackDescriptor;
import link.botwmcs.ltsxassistant.api.soundengine.NowPlayingSnapshot;
import link.botwmcs.ltsxassistant.net.soundengine.MusicControlAction;
import link.botwmcs.ltsxassistant.net.soundengine.MusicControlPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.gui.ModListScreen;

/**
 * Hard-takeover music controller with classic + modern album driver support.
 */
public final class AssistantMusicEngineService
        implements MusicPlaybackApi, MusicSceneApi, MusicCoverApi, MusicAlbumApi {
    private static final String ID_UNKNOWN = "minecraft:unknown";
    private static final int STARTING_DELAY = 100;
    private static final String CLASSIC_ALBUM_ID = "minecraft_classic";
    private static final String SCENE_TITLE = "menu.title";
    private static final String SCENE_WORLD_SELECT = "menu.world_select";
    private static final String SCENE_OPTIONS = "menu.options";
    private static final String SCENE_MOD_LIST = "menu.mod_list";
    private static final String SCENE_MULTIPLAYER = "menu.multiplayer";
    private static final String SCENE_PAUSE = "ingame.pause";
    private static final ResourceLocation FALLBACK_COVER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/item/music_disc_11.png");

    private final AtomicReference<NowPlayingSnapshot> nowPlaying = new AtomicReference<>(NowPlayingSnapshot.stopped());
    private final RandomSource random = RandomSource.create();
    private final AssistantModernMusicPlayer modernPlayer = new AssistantModernMusicPlayer();
    private final AssistantAlbumCatalog albumCatalog = new AssistantAlbumCatalog();

    @Nullable
    private SoundInstance activeClassicInstance;
    @Nullable
    private ResourceLocation activeClassicSoundId;
    @Nullable
    private Music manualClassicMusic;
    private MusicEngineMode requestedMode = MusicEngineMode.CLASSIC;
    private String requestedAlbumId = "";
    private String requestedTrackId = "";
    private boolean manualPaused;
    private int nextSongDelay = STARTING_DELAY;
    private long activeTrackStartMillis;
    private String modernObservedTrackId = "";
    private int modernInactiveTicks;
    private boolean modernWasActive;

    @Override
    public void play(MusicEngineMode mode, String albumId, String trackId, int stemTrack) {
        requestedMode = mode;
        requestedAlbumId = safe(albumId);
        requestedTrackId = mode == MusicEngineMode.MODERN ? canonicalModernTrackId(trackId) : safe(trackId);
        manualPaused = false;

        if (mode == MusicEngineMode.CLASSIC) {
            stopModernPlayback();
            manualClassicMusic = parseClassicMusicFromTrackId(trackId);
            if (manualClassicMusic == null) {
                manualClassicMusic = parseClassicMusicFromTrackId(albumId);
            }
            nextSongDelay = 0;
            nowPlaying.set(new NowPlayingSnapshot(MusicEngineMode.CLASSIC, CLASSIC_ALBUM_ID, safe(trackId), -1, true, 0L));
            return;
        }

        stopClassicPlayback();
        manualClassicMusic = null;
        ensureModernSelectionResolved();
        if (!requestedAlbumId.isBlank()) {
            Config.setSelectedAlbumId(requestedAlbumId);
        }

        String targetTrackId = resolveModernTrackId(requestedAlbumId, requestedTrackId);
        if (!targetTrackId.isBlank()) {
            modernPlayer.play(targetTrackId, Math.max(0, stemTrack));
            requestedTrackId = targetTrackId;
        }
        resetModernTrackObservation(requestedTrackId);
        AssistantModernMusicPlayer.ModernPlaybackSnapshot modernSnapshot = modernPlayer.snapshot();
        nowPlaying.set(new NowPlayingSnapshot(
                MusicEngineMode.MODERN,
                requestedAlbumId,
                requestedTrackId,
                modernSnapshot.stemTrack(),
                modernSnapshot.playing(),
                modernSnapshot.timelineMillis()
        ));
    }

    @Override
    public void pause() {
        manualPaused = true;
        stopClassicPlayback();
        modernPlayer.pause();
        AssistantModernMusicPlayer.ModernPlaybackSnapshot modernSnapshot = modernPlayer.snapshot();
        nowPlaying.updateAndGet(current -> new NowPlayingSnapshot(
                current.mode(),
                current.albumId(),
                current.trackId(),
                current.mode() == MusicEngineMode.MODERN ? modernSnapshot.stemTrack() : current.stemTrack(),
                false,
                current.mode() == MusicEngineMode.MODERN ? modernSnapshot.timelineMillis() : current.timelineMillis()
        ));
    }

    @Override
    public void resume() {
        manualPaused = false;
        if (requestedMode == MusicEngineMode.CLASSIC) {
            nextSongDelay = 0;
        } else {
            modernPlayer.resume();
        }
        AssistantModernMusicPlayer.ModernPlaybackSnapshot modernSnapshot = modernPlayer.snapshot();
        nowPlaying.updateAndGet(current -> new NowPlayingSnapshot(
                current.mode(),
                current.albumId(),
                current.trackId(),
                current.mode() == MusicEngineMode.MODERN ? modernSnapshot.stemTrack() : current.stemTrack(),
                current.mode() == MusicEngineMode.MODERN ? modernSnapshot.playing() : true,
                current.mode() == MusicEngineMode.MODERN ? modernSnapshot.timelineMillis() : current.timelineMillis()
        ));
    }

    @Override
    public void stop() {
        manualPaused = true;
        manualClassicMusic = null;
        stopClassicPlayback();
        stopModernPlayback();
        resetModernTrackObservation("");
        requestedMode = Config.preferredMusicEngineMode();
        requestedAlbumId = "";
        requestedTrackId = "";
        nowPlaying.set(NowPlayingSnapshot.stopped());
    }

    @Override
    public void setTrack(MusicEngineMode mode, String albumId, String trackId) {
        requestedMode = mode;
        requestedAlbumId = safe(albumId);
        requestedTrackId = mode == MusicEngineMode.MODERN ? canonicalModernTrackId(trackId) : safe(trackId);

        if (mode == MusicEngineMode.CLASSIC) {
            stopModernPlayback();
            manualClassicMusic = parseClassicMusicFromTrackId(trackId);
            if (manualClassicMusic == null) {
                manualClassicMusic = parseClassicMusicFromTrackId(albumId);
            }
            nextSongDelay = 0;
            return;
        }

        stopClassicPlayback();
        manualClassicMusic = null;
        ensureModernSelectionResolved();
        if (!requestedAlbumId.isBlank()) {
            Config.setSelectedAlbumId(requestedAlbumId);
        }

        String targetTrackId = resolveModernTrackId(requestedAlbumId, requestedTrackId);
        if (!targetTrackId.isBlank()) {
            requestedTrackId = targetTrackId;
            int currentStem = Math.max(0, nowPlaying.get().stemTrack());
            modernPlayer.play(targetTrackId, currentStem);
            resetModernTrackObservation(requestedTrackId);
            if (manualPaused) {
                modernPlayer.pause();
            }
        }

        AssistantModernMusicPlayer.ModernPlaybackSnapshot modernSnapshot = modernPlayer.snapshot();
        nowPlaying.updateAndGet(current -> new NowPlayingSnapshot(
                mode,
                requestedAlbumId,
                requestedTrackId,
                modernSnapshot.stemTrack(),
                !manualPaused && modernSnapshot.playing(),
                modernSnapshot.timelineMillis()
        ));
    }

    @Override
    public void setStemTrack(int stemTrack) {
        if (requestedMode == MusicEngineMode.MODERN) {
            modernPlayer.setStemTrack(stemTrack);
        }
        AssistantModernMusicPlayer.ModernPlaybackSnapshot modernSnapshot = modernPlayer.snapshot();
        nowPlaying.updateAndGet(current -> new NowPlayingSnapshot(
                current.mode(),
                current.albumId(),
                current.trackId(),
                current.mode() == MusicEngineMode.MODERN ? modernSnapshot.stemTrack() : stemTrack,
                current.playing(),
                current.mode() == MusicEngineMode.MODERN ? modernSnapshot.timelineMillis() : current.timelineMillis()
        ));
    }

    @Override
    public NowPlayingSnapshot nowPlaying() {
        return nowPlaying.get();
    }

    @Override
    public String currentSceneId() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen != null) {
            return classifyScreenScene(screen);
        }
        if (minecraft.player == null || minecraft.level == null) {
            return "menu.idle";
        }
        if (underwater()) {
            return "ingame.underwater";
        }
        ResourceLocation dimension = minecraft.player.level().dimension().location();
        if (dimension.equals(Level.OVERWORLD.location())) {
            return "ingame.overworld";
        }
        if (dimension.equals(Level.NETHER.location())) {
            return "ingame.nether";
        }
        if (dimension.equals(Level.END.location())) {
            return "ingame.end";
        }
        return "ingame.dimension." + dimension.getNamespace() + "." + dimension.getPath();
    }

    @Override
    public String currentScreenClassName() {
        Screen screen = Minecraft.getInstance().screen;
        return screen == null ? "" : screen.getClass().getName();
    }

    @Override
    public boolean inWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null;
    }

    @Override
    public boolean underwater() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isUnderWater();
    }

    @Override
    public String dimensionId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return ID_UNKNOWN;
        }
        return minecraft.player.level().dimension().location().toString();
    }

    @Override
    public String biomeId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return ID_UNKNOWN;
        }
        Holder<Biome> biomeHolder = minecraft.player.level().getBiome(minecraft.player.blockPosition());
        return biomeHolder.unwrapKey().map(resourceKey -> resourceKey.location().toString()).orElse(ID_UNKNOWN);
    }

    @Override
    public ResourceLocation currentCoverTexture() {
        NowPlayingSnapshot snapshot = nowPlaying.get();
        if (snapshot.mode() == MusicEngineMode.MODERN && !snapshot.albumId().isBlank()) {
            ResourceLocation albumCover = albumCatalog.coverTexture(snapshot.albumId());
            if (albumCover != null) {
                return albumCover;
            }
        }
        ResourceLocation candidate = resolveCoverFromTrack(snapshot.trackId(), snapshot.albumId());
        return existsTexture(candidate) ? candidate : FALLBACK_COVER_TEXTURE;
    }

    @Override
    public List<MusicAlbumDescriptor> albums() {
        return albumCatalog.albums();
    }

    @Override
    public List<MusicTrackDescriptor> tracks(String albumId) {
        return albumCatalog.tracks(safe(albumId));
    }

    @Override
    public String selectedAlbumId() {
        ensureModernSelectionResolved();
        return requestedAlbumId;
    }

    @Override
    public void selectAlbum(String albumId) {
        String normalized = safe(albumId);
        if (normalized.isBlank()) {
            return;
        }
        if (!albumCatalog.hasAlbum(normalized)) {
            return;
        }
        requestedAlbumId = normalized;
        Config.setSelectedAlbumId(normalized);
        requestedTrackId = albumCatalog.firstTrack(normalized)
                .map(MusicTrackDescriptor::trackId)
                .map(AssistantMusicEngineService::canonicalModernTrackId)
                .orElse("");
        if (requestedMode == MusicEngineMode.MODERN && !requestedTrackId.isBlank()) {
            modernPlayer.play(requestedTrackId, Math.max(0, nowPlaying.get().stemTrack()));
            resetModernTrackObservation(requestedTrackId);
            if (manualPaused) {
                modernPlayer.pause();
            }
        }
    }

    @Override
    public void playTrack(String albumId, String trackId) {
        String normalizedAlbum = safe(albumId);
        String normalizedTrack = safe(trackId);
        Optional<MusicTrackDescriptor> resolved = albumCatalog.resolveTrack(normalizedAlbum, normalizedTrack);
        if (resolved.isEmpty()) {
            return;
        }
        MusicTrackDescriptor track = resolved.get();
        requestedMode = MusicEngineMode.MODERN;
        requestedAlbumId = track.albumId();
        requestedTrackId = canonicalModernTrackId(track.trackId());
        Config.setSelectedAlbumId(requestedAlbumId);
        manualPaused = false;
        stopClassicPlayback();
        modernPlayer.play(requestedTrackId, Math.max(0, nowPlaying.get().stemTrack()));
        resetModernTrackObservation(requestedTrackId);
    }

    @Override
    public void nextTrack() {
        ensureModernSelectionResolved();
        if (requestedAlbumId.isBlank()) {
            return;
        }
        String currentTrack = requestedTrackId.isBlank() ? nowPlaying.get().trackId() : requestedTrackId;
        Optional<MusicTrackDescriptor> next = albumCatalog.cycleTrack(requestedAlbumId, currentTrack, 1);
        next.ifPresent(track -> playTrack(track.albumId(), track.trackId()));
    }

    @Override
    public void previousTrack() {
        ensureModernSelectionResolved();
        if (requestedAlbumId.isBlank()) {
            return;
        }
        String currentTrack = requestedTrackId.isBlank() ? nowPlaying.get().trackId() : requestedTrackId;
        Optional<MusicTrackDescriptor> prev = albumCatalog.cycleTrack(requestedAlbumId, currentTrack, -1);
        prev.ifPresent(track -> playTrack(track.albumId(), track.trackId()));
    }

    public void markAlbumCatalogDirty() {
        albumCatalog.markDirty();
    }

    public void applyControlPayload(MusicControlPayload payload) {
        MusicControlAction action = MusicControlAction.fromSerializedName(payload.action());
        switch (action) {
            case PLAY -> play(
                    parseMode(payload.mode()),
                    payload.albumId().orElse(""),
                    payload.trackId().orElse(""),
                    payload.stemTrack().orElse(-1)
            );
            case PAUSE -> pause();
            case RESUME -> resume();
            case STOP -> stop();
            case SET_TRACK -> setTrack(
                    parseMode(payload.mode()),
                    payload.albumId().orElse(""),
                    payload.trackId().orElse("")
            );
            case SET_STEM -> setStemTrack(payload.stemTrack().orElse(-1));
        }
    }

    public void tickEngine() {
        if (requestedAlbumId.isBlank() && requestedTrackId.isBlank()) {
            requestedMode = Config.preferredMusicEngineMode();
        }

        if (requestedMode == MusicEngineMode.MODERN) {
            tickModernEngine();
            return;
        }

        tickClassicEngine();
    }

    public Optional<Music> resolveScreenBackgroundMusic(Screen screen) {
        return Optional.empty();
    }

    private void tickModernEngine() {
        stopClassicPlayback();
        ensureModernSelectionResolved();

        AssistantModernMusicPlayer.ModernPlaybackSnapshot modernSnapshot = modernPlayer.snapshot();
        String targetTrack = canonicalModernTrackId(resolveModernTrackId(requestedAlbumId, requestedTrackId));
        String currentTrack = canonicalModernTrackId(modernSnapshot.trackId());
        if (!targetTrack.isBlank() && !targetTrack.equalsIgnoreCase(currentTrack)) {
            modernPlayer.play(targetTrack, Math.max(0, modernSnapshot.stemTrack()));
            requestedTrackId = targetTrack;
            resetModernTrackObservation(requestedTrackId);
            modernSnapshot = modernPlayer.snapshot();
        }

        boolean screenPause = Minecraft.getInstance().screen instanceof PauseScreen;
        boolean effectivePaused = manualPaused || (screenPause && Config.pauseScreenPausesMusic());

        int stemForScene = resolveSceneStemTrack();
        if (stemForScene >= 0 && modernSnapshot.supportsStemSwitching() && stemForScene != modernSnapshot.stemTrack()) {
            modernPlayer.setStemTrack(stemForScene);
        }

        if (effectivePaused) {
            modernPlayer.pause();
        } else {
            modernPlayer.resume();
        }

        modernSnapshot = modernPlayer.snapshot();
        updateModernTrackObservation(modernSnapshot, effectivePaused);
        if (!effectivePaused
                && modernWasActive
                && modernInactiveTicks >= 40
                && !modernSnapshot.trackId().isBlank()
                && !modernSnapshot.supportsStemSwitching()) {
            nextTrack();
            resetModernTrackObservation(requestedTrackId);
            modernSnapshot = modernPlayer.snapshot();
        }

        nowPlaying.set(new NowPlayingSnapshot(
                MusicEngineMode.MODERN,
                requestedAlbumId,
                requestedTrackId,
                modernSnapshot.stemTrack(),
                !effectivePaused && modernSnapshot.playing(),
                modernSnapshot.timelineMillis()
        ));
    }

    private void tickClassicEngine() {
        if (manualPaused) {
            updatePlaybackSnapshot(false, 0L);
            return;
        }

        Music targetMusic = manualClassicMusic != null ? manualClassicMusic : resolveSituationalClassicMusic();
        if (targetMusic == null) {
            stopClassicPlayback();
            nextSongDelay = 0;
            updatePlaybackSnapshot(false, 0L);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        SoundManager soundManager = minecraft.getSoundManager();
        ResourceLocation targetSound = targetMusic.getEvent().value().getLocation();

        if (activeClassicInstance != null) {
            if (!targetSound.equals(activeClassicSoundId) && targetMusic.replaceCurrentMusic()) {
                soundManager.stop(activeClassicInstance);
                activeClassicInstance = null;
                activeClassicSoundId = null;
                nextSongDelay = Mth.nextInt(random, 0, Math.max(1, targetMusic.getMinDelay() / 2));
            }

            if (activeClassicInstance != null && !soundManager.isActive(activeClassicInstance)) {
                activeClassicInstance = null;
                activeClassicSoundId = null;
                nextSongDelay = Math.min(nextSongDelay, Mth.nextInt(random, targetMusic.getMinDelay(), targetMusic.getMaxDelay()));
            }
        }

        nextSongDelay = Math.min(nextSongDelay, targetMusic.getMaxDelay());
        if (activeClassicInstance == null && nextSongDelay-- <= 0) {
            startClassic(targetMusic);
        }

        if (activeClassicInstance != null && soundManager.isActive(activeClassicInstance)) {
            updatePlaybackSnapshot(true, Math.max(0L, Util.getMillis() - activeTrackStartMillis));
        } else {
            updatePlaybackSnapshot(false, 0L);
        }
    }

    private void ensureModernSelectionResolved() {
        albumCatalog.ensureReady();

        if (requestedAlbumId.isBlank() || !albumCatalog.hasAlbum(requestedAlbumId)) {
            String configured = safe(Config.selectedAlbumId());
            if (!configured.isBlank() && albumCatalog.hasAlbum(configured)) {
                requestedAlbumId = configured;
            } else {
                List<MusicAlbumDescriptor> available = albumCatalog.albums();
                requestedAlbumId = available.isEmpty() ? "" : available.get(0).albumId();
            }
            if (!requestedAlbumId.isBlank()) {
                Config.setSelectedAlbumId(requestedAlbumId);
            }
        }

        if (!requestedAlbumId.isBlank()) {
            Optional<MusicTrackDescriptor> resolved = albumCatalog.resolveTrack(requestedAlbumId, requestedTrackId);
            if (resolved.isPresent()) {
                requestedTrackId = canonicalModernTrackId(resolved.get().trackId());
            } else {
                requestedTrackId = albumCatalog.firstTrack(requestedAlbumId)
                        .map(MusicTrackDescriptor::trackId)
                        .map(AssistantMusicEngineService::canonicalModernTrackId)
                        .orElse("");
            }
        } else {
            requestedTrackId = "";
        }
    }

    private int resolveSceneStemTrack() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen instanceof OptionsScreen || (screen != null && screen.getClass().getName().startsWith("net.minecraft.client.gui.screens.options."))) {
            return 0; // 01/02
        }
        if (screen instanceof PauseScreen) {
            return 1; // 03/04
        }
        if (screen instanceof TitleScreen) {
            return 3; // 07/08
        }
        if (screen != null) {
            return 2; // 05/06
        }
        if (minecraft.level != null) {
            return minecraft.level.isDay() ? 3 : 1; // day 07/08, night 03/04
        }
        return 3;
    }

    private void startClassic(Music music) {
        SoundEvent event = music.getEvent().value();
        SoundInstance instance = SimpleSoundInstance.forMusic(event);
        Minecraft.getInstance().getSoundManager().play(instance);
        activeClassicInstance = instance;
        activeClassicSoundId = event.getLocation();
        activeTrackStartMillis = Util.getMillis();
        nextSongDelay = Integer.MAX_VALUE;
        nowPlaying.set(new NowPlayingSnapshot(
                MusicEngineMode.CLASSIC,
                CLASSIC_ALBUM_ID,
                activeClassicSoundId.toString(),
                -1,
                true,
                0L
        ));
    }

    private void stopClassicPlayback() {
        if (activeClassicInstance != null) {
            Minecraft.getInstance().getSoundManager().stop(activeClassicInstance);
            activeClassicInstance = null;
            activeClassicSoundId = null;
        }
        nextSongDelay = STARTING_DELAY;
    }

    private void stopModernPlayback() {
        modernPlayer.stop();
    }

    @Nullable
    private Music resolveSituationalClassicMusic() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen instanceof PauseScreen && Config.pauseScreenPausesMusic()) {
            return null;
        }
        return minecraft.getSituationalMusic();
    }

    private static String classifyScreenScene(Screen screen) {
        if (screen instanceof TitleScreen) {
            return SCENE_TITLE;
        }
        if (screen instanceof SelectWorldScreen) {
            return SCENE_WORLD_SELECT;
        }
        if (screen instanceof OptionsScreen || screen.getClass().getName().startsWith("net.minecraft.client.gui.screens.options.")) {
            return SCENE_OPTIONS;
        }
        if (screen instanceof ModListScreen) {
            return SCENE_MOD_LIST;
        }
        if (screen instanceof JoinMultiplayerScreen || screen.getClass().getName().startsWith("net.minecraft.client.gui.screens.multiplayer.")) {
            return SCENE_MULTIPLAYER;
        }
        if (screen instanceof PauseScreen) {
            return SCENE_PAUSE;
        }
        return "screen." + screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private void updatePlaybackSnapshot(boolean playing, long timelineMillis) {
        nowPlaying.updateAndGet(current -> new NowPlayingSnapshot(
                current.mode(),
                current.albumId(),
                current.trackId(),
                current.stemTrack(),
                playing,
                timelineMillis
        ));
    }

    @Nullable
    private static Music parseClassicMusicFromTrackId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation soundId = ResourceLocation.tryParse(raw);
        if (soundId == null) {
            return null;
        }
        Optional<SoundEvent> soundEvent = BuiltInRegistries.SOUND_EVENT.getOptional(soundId);
        if (soundEvent.isEmpty()) {
            return null;
        }
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEvent.get());
        return new Music(holder, 20, 600, true);
    }

    private static MusicEngineMode parseMode(Optional<String> raw) {
        return MusicEngineMode.fromSerializedName(raw.orElse(MusicEngineMode.CLASSIC.serializedName()));
    }

    private static String resolveModernTrackId(String albumId, String trackId) {
        if (trackId != null && !trackId.isBlank()) {
            return canonicalModernTrackId(trackId);
        }
        return canonicalModernTrackId(albumId);
    }

    private static String safe(String raw) {
        return raw == null ? "" : raw;
    }

    private void resetModernTrackObservation(String trackId) {
        modernObservedTrackId = canonicalModernTrackId(trackId);
        modernInactiveTicks = 0;
        modernWasActive = false;
    }

    private void updateModernTrackObservation(AssistantModernMusicPlayer.ModernPlaybackSnapshot snapshot, boolean effectivePaused) {
        String currentTrack = canonicalModernTrackId(snapshot.trackId());
        if (!currentTrack.equalsIgnoreCase(modernObservedTrackId)) {
            modernObservedTrackId = currentTrack;
            modernInactiveTicks = 0;
            modernWasActive = false;
        }
        if (snapshot.playing()) {
            modernWasActive = true;
            modernInactiveTicks = 0;
            return;
        }
        if (effectivePaused || !modernWasActive || currentTrack.isBlank()) {
            modernInactiveTicks = 0;
            return;
        }
        modernInactiveTicks++;
    }

    private static String canonicalModernTrackId(String rawTrackId) {
        if (rawTrackId == null || rawTrackId.isBlank()) {
            return "";
        }
        ResourceLocation base = ResourceLocation.tryParse(rawTrackId.trim().replace('\\', '/'));
        if (base == null) {
            return rawTrackId.trim().replace('\\', '/');
        }
        String path = base.getPath().replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith("sounds/")) {
            path = path.substring("sounds/".length());
        }
        if (path.endsWith(".ogg")) {
            path = path.substring(0, path.length() - 4);
        } else if (path.endsWith(".wav")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.isBlank()) {
            return "";
        }
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), path).toString();
    }

    private static ResourceLocation resolveCoverFromTrack(String trackId, String albumId) {
        ResourceLocation source = ResourceLocation.tryParse(
                (trackId != null && !trackId.isBlank()) ? trackId : safe(albumId)
        );
        if (source == null) {
            return FALLBACK_COVER_TEXTURE;
        }
        String path = source.getPath().replace('\\', '/');
        if (path.startsWith("sounds/")) {
            path = path.substring("sounds/".length());
        }
        if (path.startsWith("soundengine/modern/")) {
            path = path.substring("soundengine/modern/".length());
        }
        if (path.endsWith(".ogg")) {
            path = path.substring(0, path.length() - 4);
        } else if (path.endsWith(".wav")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.isBlank()) {
            return FALLBACK_COVER_TEXTURE;
        }
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), "textures/soundengine/covers/" + path + ".png");
    }

    private static boolean existsTexture(ResourceLocation texture) {
        if (texture == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        try (InputStream ignored = minecraft.getResourceManager().open(texture)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
