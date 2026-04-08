package link.botwmcs.ltsxassistant.service.soundengine;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.api.soundengine.MusicAlbumDescriptor;
import link.botwmcs.ltsxassistant.api.soundengine.MusicTrackDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Resource-pack driven album catalog for assets/ltsxassistant/sounds/music.
 */
public final class AssistantAlbumCatalog {
    private static final String ALBUM_NAMESPACE = LTSXAssistant.MODID;
    private static final String MUSIC_ROOT = "sounds/music";
    private static final ResourceLocation FALLBACK_COVER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/item/music_disc_11.png");
    private static final AtomicBoolean GLOBAL_DIRTY = new AtomicBoolean(true);

    private final Random random = new Random();
    private final Map<String, AlbumEntry> albums = new LinkedHashMap<>();
    private boolean dirty = true;

    public static void markGlobalDirty() {
        GLOBAL_DIRTY.set(true);
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    public synchronized void ensureReady() {
        if (GLOBAL_DIRTY.getAndSet(false)) {
            dirty = true;
        }
        if (!dirty) {
            return;
        }
        rebuildCatalog();
        dirty = false;
    }

    public synchronized List<MusicAlbumDescriptor> albums() {
        ensureReady();
        List<MusicAlbumDescriptor> result = new ArrayList<>(albums.size());
        for (AlbumEntry album : albums.values()) {
            result.add(album.descriptor);
        }
        return result;
    }

    public synchronized List<MusicTrackDescriptor> tracks(String albumId) {
        ensureReady();
        AlbumEntry album = albums.get(albumId);
        if (album == null) {
            return List.of();
        }
        return album.trackDescriptors;
    }

    public synchronized Optional<MusicTrackDescriptor> firstTrack(String albumId) {
        ensureReady();
        AlbumEntry album = albums.get(albumId);
        if (album == null || album.orderedTrackIds.isEmpty()) {
            return Optional.empty();
        }
        String trackId = album.orderedTrackIds.get(0);
        return Optional.ofNullable(album.trackById.get(trackId));
    }

    public synchronized Optional<MusicTrackDescriptor> resolveTrack(String albumId, String trackId) {
        ensureReady();
        AlbumEntry album = albums.get(albumId);
        if (album == null) {
            return Optional.empty();
        }
        MusicTrackDescriptor direct = album.trackById.get(trackId);
        if (direct != null) {
            return Optional.of(direct);
        }
        if (trackId == null || trackId.isBlank()) {
            return firstTrack(albumId);
        }
        for (MusicTrackDescriptor descriptor : album.trackDescriptors) {
            if (descriptor.trackId().equalsIgnoreCase(trackId)) {
                return Optional.of(descriptor);
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<MusicTrackDescriptor> cycleTrack(String albumId, String currentTrackId, int delta) {
        ensureReady();
        AlbumEntry album = albums.get(albumId);
        if (album == null || album.orderedTrackIds.isEmpty()) {
            return Optional.empty();
        }
        if (delta == 0) {
            return resolveTrack(albumId, currentTrackId);
        }
        int size = album.orderedTrackIds.size();
        int index = album.trackIndexById.getOrDefault(currentTrackId, -1);
        if (index < 0) {
            index = 0;
        }
        int nextIndex = Math.floorMod(index + delta, size);
        String nextTrackId = album.orderedTrackIds.get(nextIndex);
        return Optional.ofNullable(album.trackById.get(nextTrackId));
    }

    public synchronized ResourceLocation coverTexture(String albumId) {
        ensureReady();
        AlbumEntry album = albums.get(albumId);
        if (album == null || album.descriptor.coverTexture() == null) {
            return FALLBACK_COVER_TEXTURE;
        }
        return album.descriptor.coverTexture();
    }

    public synchronized boolean hasAlbum(String albumId) {
        ensureReady();
        return albums.containsKey(albumId);
    }

    private void rebuildCatalog() {
        albums.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ResourceManager resourceManager = minecraft.getResourceManager();
        if (resourceManager == null) {
            return;
        }

        for (PackResources pack : resourceManager.listPacks().toList()) {
            AlbumScanResult scan = scanPack(pack);
            if (scan == null || scan.tracks.isEmpty()) {
                continue;
            }
            String uniqueAlbumId = uniqueAlbumId(scan.albumId);
            ResourceLocation cover = loadPackCoverTexture(uniqueAlbumId, pack);
            AlbumEntry entry = buildAlbumEntry(uniqueAlbumId, scan.displayName, cover, scan.tracks);
            albums.put(uniqueAlbumId, entry);
        }
    }

    @Nullable
    private AlbumScanResult scanPack(PackResources pack) {
        if (!pack.getNamespaces(PackType.CLIENT_RESOURCES).contains(ALBUM_NAMESPACE)) {
            return null;
        }

        String baseAlbumId = readPackId(pack);
        String displayName = readPackDisplayName(pack, baseAlbumId);
        List<TrackEntry> tracks = new ArrayList<>();

        pack.listResources(PackType.CLIENT_RESOURCES, ALBUM_NAMESPACE, MUSIC_ROOT, (resourceId, inputSupplier) -> {
            String path = resourceId.getPath().toLowerCase(Locale.ROOT);
            if (!path.endsWith(".wav") && !path.endsWith(".ogg")) {
                return;
            }
            try {
                byte[] bytes;
                try (InputStream input = inputSupplier.get()) {
                    bytes = input.readAllBytes();
                }
                tracks.add(buildTrackEntry(baseAlbumId, resourceId, bytes));
            } catch (Exception ignored) {
                // Skip broken entries.
            }
        });

        if (tracks.isEmpty()) {
            return null;
        }
        return new AlbumScanResult(baseAlbumId, displayName, tracks);
    }

    private AlbumEntry buildAlbumEntry(String albumId, String displayName, ResourceLocation coverTexture, List<TrackEntry> tracks) {
        List<TrackEntry> orderedTracks = new ArrayList<>(tracks);
        orderedTracks.sort((left, right) -> left.trackId.compareToIgnoreCase(right.trackId));

        List<TrackEntry> numbered = new ArrayList<>();
        List<TrackEntry> unnumbered = new ArrayList<>();
        for (TrackEntry track : orderedTracks) {
            if (track.trackNumber > 0) {
                numbered.add(track);
            } else {
                unnumbered.add(track);
            }
        }
        numbered.sort((left, right) -> Integer.compare(left.trackNumber, right.trackNumber));
        Collections.shuffle(unnumbered, new Random(31L * albumId.hashCode() + tracks.size()));

        Map<Integer, TrackEntry> byTrackNo = new LinkedHashMap<>();
        for (TrackEntry track : numbered) {
            if (!byTrackNo.containsKey(track.trackNumber)) {
                byTrackNo.put(track.trackNumber, track);
            } else {
                unnumbered.add(track);
            }
        }

        int maxTrackNo = 0;
        for (Integer key : byTrackNo.keySet()) {
            if (key != null) {
                maxTrackNo = Math.max(maxTrackNo, key);
            }
        }

        List<TrackEntry> playbackOrder = new ArrayList<>(tracks.size());
        int unnumberedCursor = 0;
        for (int trackNo = 1; trackNo <= maxTrackNo; trackNo++) {
            TrackEntry exact = byTrackNo.get(trackNo);
            if (exact != null) {
                playbackOrder.add(exact);
            } else if (unnumberedCursor < unnumbered.size()) {
                playbackOrder.add(unnumbered.get(unnumberedCursor++));
            }
        }
        for (TrackEntry track : byTrackNo.values()) {
            if (!playbackOrder.contains(track)) {
                playbackOrder.add(track);
            }
        }
        while (unnumberedCursor < unnumbered.size()) {
            playbackOrder.add(unnumbered.get(unnumberedCursor++));
        }

        Map<String, MusicTrackDescriptor> trackById = new LinkedHashMap<>();
        Map<String, Integer> trackIndexById = new LinkedHashMap<>();
        List<MusicTrackDescriptor> descriptors = new ArrayList<>(playbackOrder.size());
        List<String> orderedTrackIds = new ArrayList<>(playbackOrder.size());
        for (int index = 0; index < playbackOrder.size(); index++) {
            TrackEntry track = playbackOrder.get(index);
            MusicTrackDescriptor descriptor = new MusicTrackDescriptor(
                    albumId,
                    track.trackId,
                    track.displayName,
                    track.trackNumber,
                    track.stemPairs,
                    track.format
            );
            descriptors.add(descriptor);
            trackById.put(track.trackId, descriptor);
            orderedTrackIds.add(track.trackId);
            trackIndexById.put(track.trackId, index);
        }

        MusicAlbumDescriptor albumDescriptor = new MusicAlbumDescriptor(albumId, displayName, coverTexture, descriptors.size());
        return new AlbumEntry(albumDescriptor, descriptors, orderedTrackIds, trackById, trackIndexById);
    }

    private TrackEntry buildTrackEntry(String albumId, ResourceLocation resourceId, byte[] bytes) {
        String path = resourceId.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String bareName = stripFileExtension(fileName);
        String ext = fileName.toLowerCase(Locale.ROOT).endsWith(".wav") ? "wav" : "ogg";

        int trackNumber = -1;
        String displayName = bareName;
        int stemPairs = 1;

        if ("wav".equals(ext)) {
            WavTagInfo tag = readWavTagInfo(bytes);
            if (tag.trackNumber > 0) {
                trackNumber = tag.trackNumber;
            }
            if (tag.title != null && !tag.title.isBlank()) {
                displayName = tag.title;
            }
            if (tag.channels >= 2) {
                stemPairs = Math.max(1, Math.min(16, tag.channels / 2));
            }
        } else {
            int inferred = inferTrackNumberFromName(bareName);
            if (inferred > 0) {
                trackNumber = inferred;
            }
        }

        String normalizedPath = path.replace('\\', '/');
        if (normalizedPath.startsWith("sounds/")) {
            normalizedPath = normalizedPath.substring("sounds/".length());
        }
        normalizedPath = stripFileExtension(normalizedPath);
        String trackId = ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), normalizedPath).toString();
        return new TrackEntry(albumId, trackId, displayName, trackNumber, stemPairs, ext);
    }

    private ResourceLocation loadPackCoverTexture(String albumId, PackResources pack) {
        try {
            IoSupplier<InputStream> root = readRootResource(pack, "pack.png");
            if (root == null) {
                return FALLBACK_COVER_TEXTURE;
            }
            try (InputStream input = root.get()) {
                NativeImage image = NativeImage.read(input);
                ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(
                        LTSXAssistant.MODID,
                        "dynamic/music_album_cover/" + sanitizeId(albumId)
                );
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null) {
                    return FALLBACK_COVER_TEXTURE;
                }
                minecraft.getTextureManager().register(textureId, new DynamicTexture(image));
                return textureId;
            }
        } catch (Exception ignored) {
            return FALLBACK_COVER_TEXTURE;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static IoSupplier<InputStream> readRootResource(PackResources pack, String name) {
        try {
            Method method = pack.getClass().getMethod("getRootResource", String.class);
            Object value = method.invoke(pack, name);
            if (value instanceof IoSupplier<?> supplier) {
                return (IoSupplier<InputStream>) supplier;
            }
        } catch (Exception ignored) {
            // Try vararg fallback below.
        }
        try {
            Method method = pack.getClass().getMethod("getRootResource", String[].class);
            Object value = method.invoke(pack, (Object) new String[]{name});
            if (value instanceof IoSupplier<?> supplier) {
                return (IoSupplier<InputStream>) supplier;
            }
        } catch (Exception ignored) {
            // No-op
        }
        return null;
    }

    private String uniqueAlbumId(String desired) {
        String normalized = sanitizeId(desired);
        if (!albums.containsKey(normalized)) {
            return normalized;
        }
        int index = 2;
        while (albums.containsKey(normalized + "_" + index)) {
            index++;
        }
        return normalized + "_" + index;
    }

    private String readPackId(PackResources pack) {
        String fromPackId = invokeString(pack, "packId");
        if (!fromPackId.isBlank()) {
            return fromPackId;
        }
        Object location = invokeObject(pack, "location");
        if (location != null) {
            String fromLocationId = invokeString(location, "id");
            if (!fromLocationId.isBlank()) {
                return fromLocationId;
            }
        }
        return "album_" + Math.abs(random.nextInt());
    }

    private String readPackDisplayName(PackResources pack, String fallbackId) {
        Object location = invokeObject(pack, "location");
        if (location != null) {
            Object title = invokeObject(location, "title");
            if (title instanceof Component component) {
                String text = component.getString();
                if (!text.isBlank()) {
                    return text;
                }
            }
            if (title != null) {
                String text = title.toString();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return fallbackId;
    }

    private static Object invokeObject(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String invokeString(Object target, String method) {
        Object value = invokeObject(target, method);
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private static String stripFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static int inferTrackNumberFromName(String baseName) {
        String digits = "";
        for (int i = baseName.length() - 1; i >= 0; i--) {
            char c = baseName.charAt(i);
            if (Character.isDigit(c)) {
                digits = c + digits;
            } else {
                break;
            }
        }
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "album";
        }
        String normalized = raw.toLowerCase(Locale.ROOT)
                .replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace(' ', '_');
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                builder.append(c);
            }
        }
        return builder.isEmpty() ? "album" : builder.toString();
    }

    private static WavTagInfo readWavTagInfo(byte[] bytes) {
        WavTagInfo info = new WavTagInfo();
        try {
            try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
                AudioFormat format = audioStream.getFormat();
                info.channels = Math.max(2, format.getChannels());
            }
        } catch (UnsupportedAudioFileException | IOException ignored) {
            info.channels = 2;
        }

        if (bytes.length < 12) {
            return info;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (!equalsAscii(bytes, 0, "RIFF") || !equalsAscii(bytes, 8, "WAVE")) {
            return info;
        }

        int cursor = 12;
        while (cursor + 8 <= bytes.length) {
            String chunkId = ascii(bytes, cursor, 4);
            int chunkSize = Math.max(0, buffer.getInt(cursor + 4));
            int chunkDataStart = cursor + 8;
            int chunkDataEnd = Math.min(bytes.length, chunkDataStart + chunkSize);
            if ("LIST".equals(chunkId) && chunkDataStart + 4 <= chunkDataEnd) {
                String listType = ascii(bytes, chunkDataStart, 4);
                if ("INFO".equals(listType)) {
                    parseInfoList(bytes, chunkDataStart + 4, chunkDataEnd, info);
                }
            }
            cursor = chunkDataStart + chunkSize + (chunkSize % 2);
        }
        return info;
    }

    private static void parseInfoList(byte[] bytes, int start, int end, WavTagInfo info) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int cursor = start;
        while (cursor + 8 <= end) {
            String itemId = ascii(bytes, cursor, 4);
            int size = Math.max(0, buffer.getInt(cursor + 4));
            int dataStart = cursor + 8;
            int dataEnd = Math.min(end, dataStart + size);
            if (dataStart >= dataEnd) {
                break;
            }
            String value = new String(bytes, dataStart, dataEnd - dataStart, StandardCharsets.UTF_8)
                    .replace("\u0000", "")
                    .trim();
            if (!value.isBlank()) {
                if ("INAM".equals(itemId) && (info.title == null || info.title.isBlank())) {
                    info.title = value;
                } else if (("ITRK".equals(itemId) || "TRCK".equals(itemId)) && info.trackNumber < 0) {
                    info.trackNumber = parseTrackNumber(value);
                }
            }
            cursor = dataStart + size + (size % 2);
        }
    }

    private static int parseTrackNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        String value = raw.trim();
        int slash = value.indexOf('/');
        if (slash > 0) {
            value = value.substring(0, slash);
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String ascii(byte[] bytes, int offset, int len) {
        if (offset < 0 || offset + len > bytes.length) {
            return "";
        }
        return new String(bytes, offset, len, StandardCharsets.US_ASCII);
    }

    private static boolean equalsAscii(byte[] bytes, int offset, String expected) {
        return expected.equals(ascii(bytes, offset, expected.length()));
    }

    private static final class AlbumScanResult {
        private final String albumId;
        private final String displayName;
        private final List<TrackEntry> tracks;

        private AlbumScanResult(String albumId, String displayName, List<TrackEntry> tracks) {
            this.albumId = albumId;
            this.displayName = displayName;
            this.tracks = tracks;
        }
    }

    private static final class TrackEntry {
        private final String albumId;
        private final String trackId;
        private final String displayName;
        private final int trackNumber;
        private final int stemPairs;
        private final String format;

        private TrackEntry(String albumId, String trackId, String displayName, int trackNumber, int stemPairs, String format) {
            this.albumId = albumId;
            this.trackId = trackId;
            this.displayName = displayName;
            this.trackNumber = trackNumber;
            this.stemPairs = stemPairs;
            this.format = format;
        }
    }

    private static final class AlbumEntry {
        private final MusicAlbumDescriptor descriptor;
        private final List<MusicTrackDescriptor> trackDescriptors;
        private final List<String> orderedTrackIds;
        private final Map<String, MusicTrackDescriptor> trackById;
        private final Map<String, Integer> trackIndexById;

        private AlbumEntry(
                MusicAlbumDescriptor descriptor,
                List<MusicTrackDescriptor> trackDescriptors,
                List<String> orderedTrackIds,
                Map<String, MusicTrackDescriptor> trackById,
                Map<String, Integer> trackIndexById
        ) {
            this.descriptor = descriptor;
            this.trackDescriptors = List.copyOf(trackDescriptors);
            this.orderedTrackIds = List.copyOf(orderedTrackIds);
            this.trackById = Map.copyOf(trackById);
            this.trackIndexById = Map.copyOf(trackIndexById);
        }
    }

    private static final class WavTagInfo {
        private int trackNumber = -1;
        private int channels = 2;
        @Nullable
        private String title;
    }
}
