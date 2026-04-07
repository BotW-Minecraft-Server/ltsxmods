package link.botwmcs.ltsxassistant.service.soundengine;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.GainProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Modern playback engine:
 * - WAV multistem backend (custom PCM + crossfade)
 * - Event backend for resource-pack OGG/WAV tracks via SoundManager
 */
public final class AssistantModernMusicPlayer {
    private static final int MAX_STEMS = 16;
    private static final int BUFFER_FRAMES = 1024;
    private static final int BYTES_PER_STEREO_FRAME = 4;
    private static final int CROSSFADE_MILLIS = 450;
    private static final int IDLE_SLEEP_MILLIS = 10;

    private final Object stateLock = new Object();

    @Nullable
    private ModernWavTrack currentTrack;
    @Nullable
    private SoundInstance activeEventInstance;
    @Nullable
    private ResourceLocation activeEventSoundId;
    @Nullable
    private SourceDataLine outputLine;
    @Nullable
    private AudioFormat outputFormat;
    @Nullable
    private DspGainPipeline gainPipeline;
    @Nullable
    private Thread audioThread;

    private String currentTrackId = "";
    private PlaybackBackend backend = PlaybackBackend.NONE;
    private long timelineFrame;
    private long timelineMillisOffset;
    private long activeStartMillis;
    private int activeStem;
    private int fadingOutStem = -1;
    private int fadeFramesRemaining;
    private int fadeFramesTotal = 1;
    private boolean paused = true;
    private boolean running;
    private boolean activelyWriting;

    public void play(String trackId, int stemTrack) {
        String normalizedTrackId = normalizeTrackId(trackId);
        if (normalizedTrackId.isEmpty()) {
            stop();
            return;
        }

        stop();

        ModernWavTrack wavTrack = loadWavTrack(normalizedTrackId);
        if (wavTrack != null) {
            synchronized (stateLock) {
                backend = PlaybackBackend.WAV_STEM;
                currentTrack = wavTrack;
                currentTrackId = normalizedTrackId;
                timelineFrame = 0L;
                timelineMillisOffset = 0L;
                activeStartMillis = Util.getMillis();
                activeStem = clampStem(stemTrack, wavTrack.stemCount());
                fadingOutStem = -1;
                fadeFramesRemaining = 0;
                fadeFramesTotal = Math.max(1, millisToFrames(CROSSFADE_MILLIS, wavTrack.sampleRate()));
                paused = false;
                running = true;
                activelyWriting = false;
                ensureAudioThread();
            }
            return;
        }

        ResourceLocation eventSoundId = parseEventSoundId(normalizedTrackId);
        if (eventSoundId == null || !canResolveSoundEvent(eventSoundId)) {
            stop();
            return;
        }
        SoundInstance instance = createMusicEventInstance(eventSoundId);
        Minecraft.getInstance().getSoundManager().play(instance);

        synchronized (stateLock) {
            backend = PlaybackBackend.EVENT;
            currentTrack = null;
            activeEventInstance = instance;
            activeEventSoundId = eventSoundId;
            currentTrackId = eventSoundId.toString();
            timelineFrame = 0L;
            timelineMillisOffset = 0L;
            activeStartMillis = Util.getMillis();
            activeStem = clampStem(stemTrack, MAX_STEMS);
            fadingOutStem = -1;
            fadeFramesRemaining = 0;
            fadeFramesTotal = 1;
            paused = false;
            running = true;
            activelyWriting = false;
        }
    }

    public void pause() {
        synchronized (stateLock) {
            if (backend == PlaybackBackend.EVENT && running && !paused) {
                timelineMillisOffset += Math.max(0L, Util.getMillis() - activeStartMillis);
                if (activeEventInstance != null) {
                    Minecraft.getInstance().getSoundManager().stop(activeEventInstance);
                    activeEventInstance = null;
                }
            }
            paused = true;
            activelyWriting = false;
        }
    }

    public void resume() {
        synchronized (stateLock) {
            if (backend == PlaybackBackend.NONE) {
                return;
            }
            if (backend == PlaybackBackend.EVENT && activeEventSoundId != null) {
                SoundInstance instance = createMusicEventInstance(activeEventSoundId);
                Minecraft.getInstance().getSoundManager().play(instance);
                activeEventInstance = instance;
                activeStartMillis = Util.getMillis();
            }
            paused = false;
            running = true;
            if (backend == PlaybackBackend.WAV_STEM) {
                ensureAudioThread();
            }
        }
    }

    public void stop() {
        Thread threadToJoin;
        @Nullable SoundInstance eventToStop;
        synchronized (stateLock) {
            paused = true;
            running = false;
            activelyWriting = false;
            backend = PlaybackBackend.NONE;
            currentTrack = null;
            eventToStop = activeEventInstance;
            activeEventInstance = null;
            activeEventSoundId = null;
            currentTrackId = "";
            timelineFrame = 0L;
            timelineMillisOffset = 0L;
            activeStartMillis = 0L;
            activeStem = 0;
            fadingOutStem = -1;
            fadeFramesRemaining = 0;
            fadeFramesTotal = 1;
            threadToJoin = audioThread;
            audioThread = null;
        }
        if (eventToStop != null) {
            Minecraft.getInstance().getSoundManager().stop(eventToStop);
        }
        if (threadToJoin != null) {
            threadToJoin.interrupt();
            try {
                threadToJoin.join(100L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        closeOutputLine();
    }

    public void setStemTrack(int stemTrack) {
        synchronized (stateLock) {
            if (backend == PlaybackBackend.EVENT) {
                activeStem = clampStem(stemTrack, MAX_STEMS);
                return;
            }
            if (currentTrack == null) {
                return;
            }
            int clamped = clampStem(stemTrack, currentTrack.stemCount());
            if (clamped == activeStem) {
                return;
            }
            fadingOutStem = activeStem;
            activeStem = clamped;
            fadeFramesTotal = Math.max(1, millisToFrames(CROSSFADE_MILLIS, currentTrack.sampleRate()));
            fadeFramesRemaining = fadeFramesTotal;
        }
    }

    public ModernPlaybackSnapshot snapshot() {
        synchronized (stateLock) {
            if (backend == PlaybackBackend.NONE) {
                return ModernPlaybackSnapshot.stopped();
            }
            if (backend == PlaybackBackend.EVENT) {
                SoundManager soundManager = Minecraft.getInstance().getSoundManager();
                boolean active = activeEventInstance != null && soundManager.isActive(activeEventInstance);
                boolean playing = running && !paused && active;
                long timelineMillis = timelineMillisOffset;
                if (!paused && running) {
                    timelineMillis += Math.max(0L, Util.getMillis() - activeStartMillis);
                }
                return new ModernPlaybackSnapshot(currentTrackId, activeStem, playing, timelineMillis);
            }
            if (currentTrack == null) {
                return ModernPlaybackSnapshot.stopped();
            }
            long timelineInLoop = currentTrack.frameCount() <= 0
                    ? 0L
                    : timelineFrame % currentTrack.frameCount();
            long timelineMillis = (timelineInLoop * 1000L) / Math.max(1, currentTrack.sampleRate());
            boolean playing = running && !paused && activelyWriting;
            return new ModernPlaybackSnapshot(currentTrackId, activeStem, playing, timelineMillis);
        }
    }

    private void ensureAudioThread() {
        if (audioThread != null && audioThread.isAlive()) {
            return;
        }
        Thread worker = new Thread(this::audioLoop, "ltsxassistant-modern-music");
        worker.setDaemon(true);
        audioThread = worker;
        worker.start();
    }

    private void audioLoop() {
        float[] activeBuffer = new float[BUFFER_FRAMES * 2];
        float[] fadeBuffer = new float[BUFFER_FRAMES * 2];
        float[] mixedBuffer = new float[BUFFER_FRAMES * 2];
        byte[] pcmBuffer = new byte[BUFFER_FRAMES * BYTES_PER_STEREO_FRAME];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                PlaybackWork work;
                synchronized (stateLock) {
                    if (!running) {
                        break;
                    }
                    if (backend != PlaybackBackend.WAV_STEM || currentTrack == null || paused) {
                        activelyWriting = false;
                        work = null;
                    } else {
                        work = new PlaybackWork(
                                currentTrack,
                                timelineFrame,
                                activeStem,
                                fadingOutStem,
                                fadeFramesRemaining,
                                Math.max(1, fadeFramesTotal)
                        );
                    }
                }
                if (work == null) {
                    sleepQuietly(IDLE_SLEEP_MILLIS);
                    continue;
                }
                if (!ensureOutputLine(work.track().sampleRate())) {
                    synchronized (stateLock) {
                        paused = true;
                        activelyWriting = false;
                    }
                    sleepQuietly(IDLE_SLEEP_MILLIS);
                    continue;
                }

                int framesToRender = Math.min(BUFFER_FRAMES, work.track().frameCount());
                renderChunk(work, framesToRender, activeBuffer, fadeBuffer, mixedBuffer);
                int byteCount = writePcmChunk(framesToRender, mixedBuffer, pcmBuffer);

                SourceDataLine line = outputLine;
                if (line == null) {
                    sleepQuietly(IDLE_SLEEP_MILLIS);
                    continue;
                }
                line.write(pcmBuffer, 0, byteCount);

                synchronized (stateLock) {
                    if (currentTrack == work.track()) {
                        timelineFrame = (timelineFrame + framesToRender) % Math.max(1, currentTrack.frameCount());
                        if (fadeFramesRemaining > 0) {
                            fadeFramesRemaining = Math.max(0, fadeFramesRemaining - framesToRender);
                            if (fadeFramesRemaining == 0) {
                                fadingOutStem = -1;
                            }
                        }
                    }
                    activelyWriting = true;
                }
            }
        } finally {
            synchronized (stateLock) {
                activelyWriting = false;
                if (Thread.currentThread() == audioThread) {
                    audioThread = null;
                }
                if (backend == PlaybackBackend.WAV_STEM) {
                    running = false;
                }
            }
            closeOutputLine();
        }
    }

    private void renderChunk(
            PlaybackWork work,
            int frames,
            float[] activeBuffer,
            float[] fadeBuffer,
            float[] mixedBuffer
    ) {
        ModernWavTrack track = work.track();
        int activeStemTrack = work.activeStem();
        int fadingStemTrack = work.fadingStem();

        for (int frame = 0; frame < frames; frame++) {
            long frameIndex = (work.startFrame() + frame) % Math.max(1, track.frameCount());
            int offset = frame * 2;
            track.copyStemStereo(frameIndex, activeStemTrack, activeBuffer, offset);
            if (work.fadeRemaining() > 0 && fadingStemTrack >= 0) {
                track.copyStemStereo(frameIndex, fadingStemTrack, fadeBuffer, offset);
            } else {
                fadeBuffer[offset] = 0.0f;
                fadeBuffer[offset + 1] = 0.0f;
            }
        }

        DspGainPipeline pipeline = gainPipeline;
        if (pipeline == null) {
            return;
        }
        if (work.fadeRemaining() > 0 && fadingStemTrack >= 0) {
            double progress = 1.0D - ((double) work.fadeRemaining() / (double) work.fadeTotal());
            double fadeInGain = clamp01(progress);
            double fadeOutGain = 1.0D - fadeInGain;
            pipeline.applyGain(activeBuffer, fadeInGain);
            pipeline.applyGain(fadeBuffer, fadeOutGain);
        }

        int sampleCount = frames * 2;
        for (int sample = 0; sample < sampleCount; sample++) {
            mixedBuffer[sample] = activeBuffer[sample] + fadeBuffer[sample];
            activeBuffer[sample] = 0.0f;
            fadeBuffer[sample] = 0.0f;
        }
        pipeline.applyMasterGain(mixedBuffer);
    }

    private int writePcmChunk(int frames, float[] mixedBuffer, byte[] pcmBuffer) {
        int sampleCount = frames * 2;
        int byteIndex = 0;
        for (int sample = 0; sample < sampleCount; sample++) {
            float clamped = clampSample(mixedBuffer[sample]);
            mixedBuffer[sample] = 0.0f;
            short pcm = (short) Math.round(clamped * Short.MAX_VALUE);
            pcmBuffer[byteIndex++] = (byte) (pcm & 0xFF);
            pcmBuffer[byteIndex++] = (byte) ((pcm >> 8) & 0xFF);
        }
        return byteIndex;
    }

    private boolean ensureOutputLine(int sampleRate) {
        AudioFormat desired = new AudioFormat(sampleRate, 16, 2, true, false);
        if (outputLine != null && outputLine.isOpen() && outputFormat != null && outputFormat.matches(desired)) {
            return true;
        }
        closeOutputLine();
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, desired);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(desired, BUFFER_FRAMES * BYTES_PER_STEREO_FRAME * 4);
            line.start();
            outputLine = line;
            outputFormat = desired;
            gainPipeline = new DspGainPipeline(sampleRate);
            return true;
        } catch (LineUnavailableException lineUnavailableException) {
            LTSXAssistant.LOGGER.warn("[ltsxassistant] Failed to open modern music output line: {}", lineUnavailableException.getMessage());
            return false;
        }
    }

    private void closeOutputLine() {
        SourceDataLine line = outputLine;
        outputLine = null;
        outputFormat = null;
        gainPipeline = null;
        if (line == null) {
            return;
        }
        try {
            line.stop();
            line.flush();
        } catch (Exception ignored) {
            // Ignore close-time errors.
        }
        line.close();
    }

    @Nullable
    private ModernWavTrack loadWavTrack(String requestedTrackId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        ResourceManager resourceManager = minecraft.getResourceManager();
        List<ResourceLocation> candidates = resolveWavCandidates(requestedTrackId);
        for (ResourceLocation candidate : candidates) {
            try (InputStream rawInput = resourceManager.open(candidate);
                 BufferedInputStream bufferedInput = new BufferedInputStream(rawInput);
                 AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bufferedInput)) {
                ModernWavTrack track = decodeWavTrack(candidate, sourceStream);
                if (track != null) {
                    return track;
                }
            } catch (IOException ignored) {
                // Try next candidate path.
            } catch (UnsupportedAudioFileException ignored) {
                // Not a PCM wav stream; try next candidate.
            } catch (Exception exception) {
                LTSXAssistant.LOGGER.warn("[ltsxassistant] Failed to load modern WAV track '{}': {}",
                        candidate,
                        exception.getMessage());
            }
        }
        return null;
    }

    private boolean canResolveSoundEvent(ResourceLocation soundId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        WeighedSoundEvents soundEvents = minecraft.getSoundManager().getSoundEvent(soundId);
        return soundEvents != null && soundEvents.getWeight() > 0;
    }

    @Nullable
    private static ModernWavTrack decodeWavTrack(ResourceLocation sourceId, AudioInputStream sourceStream) throws IOException {
        AudioFormat sourceFormat = sourceStream.getFormat();
        int sourceChannels = sourceFormat.getChannels();
        if (sourceChannels < 2) {
            return null;
        }
        AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                16,
                sourceChannels,
                sourceChannels * 2,
                sourceFormat.getSampleRate(),
                false
        );
        try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, sourceStream)) {
            byte[] pcmBytes = pcmStream.readAllBytes();
            int frameSize = pcmFormat.getFrameSize();
            if (frameSize <= 0) {
                return null;
            }
            int frameCount = pcmBytes.length / frameSize;
            if (frameCount <= 0) {
                return null;
            }
            int sampleRate = Math.max(8_000, Math.round(pcmFormat.getSampleRate()));
            float[] samples = new float[frameCount * sourceChannels];
            int byteIndex = 0;
            int sampleIndex = 0;
            for (int frame = 0; frame < frameCount; frame++) {
                for (int channel = 0; channel < sourceChannels; channel++) {
                    int low = pcmBytes[byteIndex++] & 0xFF;
                    int high = pcmBytes[byteIndex++];
                    short pcmSample = (short) ((high << 8) | low);
                    samples[sampleIndex++] = pcmSample / 32768.0f;
                }
            }
            int stemCount = Math.max(1, Math.min(MAX_STEMS, sourceChannels / 2));
            return new ModernWavTrack(sourceId, sampleRate, sourceChannels, stemCount, frameCount, samples);
        }
    }

    private static List<ResourceLocation> resolveWavCandidates(String rawTrackId) {
        ResourceLocation base = parseResourceLocation(rawTrackId);
        if (base == null) {
            return List.of();
        }
        String namespace = base.getNamespace();
        String path = stripLeadingSlash(base.getPath());
        String wavPath = path.endsWith(".wav") ? path : path + ".wav";

        Set<ResourceLocation> candidates = new LinkedHashSet<>();
        candidates.add(ResourceLocation.fromNamespaceAndPath(namespace, wavPath));
        if (!wavPath.startsWith("soundengine/modern/")) {
            candidates.add(ResourceLocation.fromNamespaceAndPath(namespace, "soundengine/modern/" + wavPath));
        }
        if (!wavPath.startsWith("sounds/")) {
            candidates.add(ResourceLocation.fromNamespaceAndPath(namespace, "sounds/" + wavPath));
        }
        return new ArrayList<>(candidates);
    }

    @Nullable
    private static ResourceLocation parseEventSoundId(String rawTrackId) {
        ResourceLocation base = parseResourceLocation(rawTrackId);
        if (base == null) {
            return null;
        }
        String path = stripLeadingSlash(base.getPath());
        if (path.startsWith("sounds/")) {
            path = path.substring("sounds/".length());
        }
        if (path.endsWith(".ogg")) {
            path = path.substring(0, path.length() - 4);
        } else if (path.endsWith(".wav")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.isBlank()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), path);
    }

    @Nullable
    private static ResourceLocation parseResourceLocation(String rawTrackId) {
        String normalized = normalizeTrackId(rawTrackId);
        if (normalized.isEmpty()) {
            return null;
        }
        ResourceLocation direct = ResourceLocation.tryParse(normalized);
        if (direct != null) {
            return direct;
        }
        if (normalized.contains(":")) {
            String[] split = normalized.split(":", 2);
            if (split.length == 2) {
                return ResourceLocation.fromNamespaceAndPath(split[0], stripLeadingSlash(split[1]));
            }
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(LTSXAssistant.MODID, stripLeadingSlash(normalized));
    }

    private static SoundInstance createMusicEventInstance(ResourceLocation soundId) {
        return new SimpleSoundInstance(
                soundId,
                SoundSource.MUSIC,
                1.0F,
                1.0F,
                SoundInstance.createUnseededRandom(),
                false,
                0,
                Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
    }

    private static int clampStem(int requestedStemTrack, int stemCount) {
        if (stemCount <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(stemCount - 1, requestedStemTrack));
    }

    private static int millisToFrames(int milliseconds, int sampleRate) {
        return Math.max(1, (sampleRate * milliseconds) / 1000);
    }

    private static float clampSample(float sample) {
        if (sample > 1.0f) {
            return 1.0f;
        }
        if (sample < -1.0f) {
            return -1.0f;
        }
        return sample;
    }

    private static double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private static String normalizeTrackId(String trackId) {
        if (trackId == null) {
            return "";
        }
        return trackId.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static String stripLeadingSlash(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private enum PlaybackBackend {
        NONE,
        WAV_STEM,
        EVENT
    }

    private record PlaybackWork(
            ModernWavTrack track,
            long startFrame,
            int activeStem,
            int fadingStem,
            int fadeRemaining,
            int fadeTotal
    ) {
    }

    private record ModernWavTrack(
            ResourceLocation resourceId,
            int sampleRate,
            int channels,
            int stemCount,
            int frameCount,
            float[] samples
    ) {
        void copyStemStereo(long frameIndex, int stemTrack, float[] target, int targetOffset) {
            int clampedStem = Math.max(0, Math.min(stemCount - 1, stemTrack));
            int pairChannel = clampedStem * 2;
            int frame = (int) (frameIndex % Math.max(1, frameCount));
            int base = frame * channels;
            int leftChannel = pairChannel;
            int rightChannel = Math.min(pairChannel + 1, channels - 1);
            target[targetOffset] = samples[base + leftChannel];
            target[targetOffset + 1] = samples[base + rightChannel];
        }
    }

    private static final class DspGainPipeline {
        private final GainProcessor stemGain = new GainProcessor(1.0D);
        private final GainProcessor masterGain = new GainProcessor(1.0D);
        private final AudioEvent stemEvent;
        private final AudioEvent masterEvent;

        private DspGainPipeline(int sampleRate) {
            TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(sampleRate, 16, 2, true, false);
            this.stemEvent = new AudioEvent(format);
            this.masterEvent = new AudioEvent(format);
        }

        private void applyGain(float[] buffer, double gain) {
            stemGain.setGain(gain);
            stemEvent.setFloatBuffer(buffer);
            stemGain.process(stemEvent);
        }

        private void applyMasterGain(float[] buffer) {
            masterGain.setGain(1.0D);
            masterEvent.setFloatBuffer(buffer);
            masterGain.process(masterEvent);
        }
    }

    public record ModernPlaybackSnapshot(
            String trackId,
            int stemTrack,
            boolean playing,
            long timelineMillis
    ) {
        public static ModernPlaybackSnapshot stopped() {
            return new ModernPlaybackSnapshot("", -1, false, 0L);
        }
    }
}
