package link.botwmcs.ltsxassistant.service.soundengine;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    private static final int WAV_FORMAT_PCM = 0x0001;
    private static final int WAV_FORMAT_IEEE_FLOAT = 0x0003;
    private static final int WAV_FORMAT_EXTENSIBLE = 0xFFFE;
    private static final int STREAM_DECODE_CHUNK_BYTES = 64 * 1024;

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
    private Thread audioThread;

    private String currentTrackId = "";
    private PlaybackBackend backend = PlaybackBackend.NONE;
    private long timelineFrame;
    private long timelineMillisOffset;
    private long activeStartMillis;
    private int activeStem;
    private int activeStemPairs;
    private int fadingOutStem = -1;
    private int fadeFramesRemaining;
    private int fadeFramesTotal = 1;
    private boolean paused = true;
    private boolean running;
    private boolean activelyWriting;
    private boolean eventSoundManagerPaused;

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
                activeStemPairs = Math.max(1, wavTrack.stemCount());
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
            activeStemPairs = 1;
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
                if (activeEventInstance != null && !eventSoundManagerPaused) {
                    Minecraft.getInstance().getSoundManager().pause();
                    eventSoundManagerPaused = true;
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
            if (!paused) {
                return;
            }
            if (backend == PlaybackBackend.EVENT && activeEventSoundId != null) {
                SoundManager soundManager = Minecraft.getInstance().getSoundManager();
                if (eventSoundManagerPaused) {
                    soundManager.resume();
                    eventSoundManagerPaused = false;
                }
                boolean active = activeEventInstance != null && soundManager.isActive(activeEventInstance);
                if (!active) {
                    SoundInstance instance = createMusicEventInstance(activeEventSoundId);
                    soundManager.play(instance);
                    activeEventInstance = instance;
                }
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
        boolean resumeSoundManager;
        synchronized (stateLock) {
            paused = true;
            running = false;
            activelyWriting = false;
            backend = PlaybackBackend.NONE;
            currentTrack = null;
            eventToStop = activeEventInstance;
            activeEventInstance = null;
            activeEventSoundId = null;
            resumeSoundManager = eventSoundManagerPaused;
            eventSoundManagerPaused = false;
            currentTrackId = "";
            timelineFrame = 0L;
            timelineMillisOffset = 0L;
            activeStartMillis = 0L;
            activeStem = 0;
            activeStemPairs = 0;
            fadingOutStem = -1;
            fadeFramesRemaining = 0;
            fadeFramesTotal = 1;
            threadToJoin = audioThread;
            audioThread = null;
        }
        if (eventToStop != null) {
            Minecraft.getInstance().getSoundManager().stop(eventToStop);
        }
        if (resumeSoundManager) {
            Minecraft.getInstance().getSoundManager().resume();
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
                return new ModernPlaybackSnapshot(currentTrackId, activeStem, activeStemPairs, false, playing, timelineMillis);
            }
            if (currentTrack == null) {
                return ModernPlaybackSnapshot.stopped();
            }
            long timelineInLoop = currentTrack.frameCount() <= 0
                    ? 0L
                    : timelineFrame % currentTrack.frameCount();
            long timelineMillis = (timelineInLoop * 1000L) / Math.max(1, currentTrack.sampleRate());
            boolean playing = running && !paused && activelyWriting;
            int stemPairs = Math.max(1, currentTrack.stemCount());
            return new ModernPlaybackSnapshot(currentTrackId, activeStem, stemPairs, stemPairs > 1, playing, timelineMillis);
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

        int sampleCount = frames * 2;
        if (work.fadeRemaining() > 0 && fadingStemTrack >= 0) {
            double progress = 1.0D - ((double) work.fadeRemaining() / (double) work.fadeTotal());
            double fadeInGain = clamp01(progress);
            double fadeOutGain = 1.0D - fadeInGain;
            applyGain(activeBuffer, sampleCount, fadeInGain);
            applyGain(fadeBuffer, sampleCount, fadeOutGain);
        }

        for (int sample = 0; sample < sampleCount; sample++) {
            mixedBuffer[sample] = activeBuffer[sample] + fadeBuffer[sample];
            activeBuffer[sample] = 0.0f;
            fadeBuffer[sample] = 0.0f;
        }
        applyGain(mixedBuffer, sampleCount, 1.0D);
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
                 BufferedInputStream bufferedInput = new BufferedInputStream(rawInput)) {
                ModernWavTrack track = decodeRiffPcmWavTrack(candidate, bufferedInput);
                if (track != null) {
                    return track;
                }
            } catch (IOException ignored) {
                // Try next candidate path.
            } catch (Exception exception) {
                LTSXAssistant.LOGGER.warn("[ltsxassistant] Failed to decode modern WAV track '{}': {}",
                        candidate,
                        exception.getMessage());
            }

            try (InputStream rawInput = resourceManager.open(candidate);
                 BufferedInputStream bufferedInput = new BufferedInputStream(rawInput);
                 AudioInputStream sourceStream = AudioSystem.getAudioInputStream(bufferedInput)) {
                ModernWavTrack track = decodeAudioSystemWavTrack(candidate, sourceStream);
                if (track != null) {
                    return track;
                }
            } catch (IOException ignored) {
                // Try next candidate path.
            } catch (UnsupportedAudioFileException ignored) {
                // Unsupported wav payload; try next candidate.
            } catch (Exception exception) {
                LTSXAssistant.LOGGER.warn("[ltsxassistant] Failed to decode fallback WAV track '{}': {}",
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
    private static ModernWavTrack decodeAudioSystemWavTrack(ResourceLocation sourceId, AudioInputStream sourceStream) throws IOException {
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
            short[] samples = new short[frameCount * sourceChannels];
            int byteIndex = 0;
            int sampleIndex = 0;
            for (int frame = 0; frame < frameCount; frame++) {
                for (int channel = 0; channel < sourceChannels; channel++) {
                    int low = pcmBytes[byteIndex++] & 0xFF;
                    int high = pcmBytes[byteIndex++];
                    short pcmSample = (short) ((high << 8) | low);
                    samples[sampleIndex++] = pcmSample;
                }
            }
            int stemCount = Math.max(1, Math.min(MAX_STEMS, sourceChannels / 2));
            return new ModernWavTrack(sourceId, sampleRate, sourceChannels, stemCount, frameCount, samples);
        }
    }

    @Nullable
    private static ModernWavTrack decodeRiffPcmWavTrack(ResourceLocation sourceId, InputStream inputStream) throws IOException {
        byte[] riffHeader = inputStream.readNBytes(12);
        if (riffHeader.length < 12 || !equalsAscii(riffHeader, 0, "RIFF") || !equalsAscii(riffHeader, 8, "WAVE")) {
            return null;
        }

        WavFormatInfo formatInfo = null;
        while (true) {
            byte[] chunkHeader = inputStream.readNBytes(8);
            if (chunkHeader.length < 8) {
                return null;
            }
            String chunkId = ascii(chunkHeader, 0, 4);
            long chunkSize = uint32LE(chunkHeader, 4);
            if (chunkSize < 0L) {
                return null;
            }

            if ("fmt ".equals(chunkId)) {
                formatInfo = parseWavFormatChunk(inputStream, chunkSize);
            } else if ("data".equals(chunkId)) {
                if (formatInfo == null) {
                    skipFully(inputStream, chunkSize);
                } else {
                    return decodeRiffDataChunk(sourceId, inputStream, chunkSize, formatInfo);
                }
            } else {
                skipFully(inputStream, chunkSize);
            }

            if ((chunkSize & 1L) != 0L) {
                if (inputStream.read() < 0) {
                    return null;
                }
            }
        }
    }

    @Nullable
    private static WavFormatInfo parseWavFormatChunk(InputStream inputStream, long chunkSize) throws IOException {
        if (chunkSize < 16L || chunkSize > Integer.MAX_VALUE) {
            skipFully(inputStream, chunkSize);
            return null;
        }
        byte[] fmtBytes = inputStream.readNBytes((int) chunkSize);
        if (fmtBytes.length < 16) {
            return null;
        }

        int rawFormatTag = uint16LE(fmtBytes, 0);
        int channels = uint16LE(fmtBytes, 2);
        int sampleRate = (int) uint32LE(fmtBytes, 4);
        int blockAlign = uint16LE(fmtBytes, 12);
        int bitsPerSample = uint16LE(fmtBytes, 14);

        int resolvedFormatTag = rawFormatTag;
        if (rawFormatTag == WAV_FORMAT_EXTENSIBLE && fmtBytes.length >= 40) {
            int validBitsPerSample = uint16LE(fmtBytes, 18);
            int subFormatTag = uint16LE(fmtBytes, 24);
            if (validBitsPerSample > 0) {
                bitsPerSample = validBitsPerSample;
            }
            if (subFormatTag > 0) {
                resolvedFormatTag = subFormatTag;
            }
        }

        int bytesPerSample = (blockAlign > 0 && channels > 0)
                ? Math.max(1, blockAlign / channels)
                : Math.max(1, (Math.max(1, bitsPerSample) + 7) / 8);
        if (bitsPerSample <= 0) {
            bitsPerSample = bytesPerSample * 8;
        }
        return new WavFormatInfo(resolvedFormatTag, channels, sampleRate, bitsPerSample, bytesPerSample, blockAlign);
    }

    @Nullable
    private static ModernWavTrack decodeRiffDataChunk(
            ResourceLocation sourceId,
            InputStream inputStream,
            long chunkSize,
            WavFormatInfo formatInfo
    ) throws IOException {
        if (formatInfo.channels() < 2) {
            skipFully(inputStream, chunkSize);
            return null;
        }

        boolean isPcm = formatInfo.formatTag() == WAV_FORMAT_PCM;
        boolean isFloat = formatInfo.formatTag() == WAV_FORMAT_IEEE_FLOAT;
        if (!isPcm && !isFloat) {
            skipFully(inputStream, chunkSize);
            return null;
        }

        int bytesPerSample = Math.max(1, formatInfo.bytesPerSample());
        int frameSize = formatInfo.blockAlign() > 0
                ? formatInfo.blockAlign()
                : formatInfo.channels() * bytesPerSample;
        if (frameSize <= 0) {
            skipFully(inputStream, chunkSize);
            return null;
        }

        long frameCountLong = chunkSize / frameSize;
        long dataBytesToDecode = frameCountLong * frameSize;
        if (frameCountLong <= 0L || frameCountLong > Integer.MAX_VALUE) {
            skipFully(inputStream, chunkSize);
            return null;
        }

        long totalSamplesLong = frameCountLong * (long) formatInfo.channels();
        if (totalSamplesLong <= 0L || totalSamplesLong > Integer.MAX_VALUE) {
            skipFully(inputStream, chunkSize);
            return null;
        }

        int frameCount = (int) frameCountLong;
        short[] samples = new short[(int) totalSamplesLong];
        int sampleIndex = 0;
        byte[] decodeBuffer = new byte[Math.max(frameSize, STREAM_DECODE_CHUNK_BYTES - (STREAM_DECODE_CHUNK_BYTES % frameSize))];
        long remainingBytes = dataBytesToDecode;

        while (remainingBytes > 0L) {
            int bytesToRead = (int) Math.min(decodeBuffer.length, remainingBytes);
            bytesToRead -= bytesToRead % frameSize;
            if (bytesToRead <= 0) {
                bytesToRead = frameSize;
            }
            int read = readFully(inputStream, decodeBuffer, 0, bytesToRead);
            if (read <= 0) {
                return null;
            }
            int alignedRead = read - (read % frameSize);
            if (alignedRead <= 0) {
                return null;
            }

            int framesInChunk = alignedRead / frameSize;
            for (int frame = 0; frame < framesInChunk; frame++) {
                int frameOffset = frame * frameSize;
                for (int channel = 0; channel < formatInfo.channels(); channel++) {
                    int sampleOffset = frameOffset + (channel * bytesPerSample);
                    short sample = decodeSampleToPcm16(
                            decodeBuffer,
                            sampleOffset,
                            bytesPerSample,
                            formatInfo.bitsPerSample(),
                            formatInfo.formatTag()
                    );
                    samples[sampleIndex++] = sample;
                }
            }

            remainingBytes -= alignedRead;
            if (read < bytesToRead) {
                return null;
            }
        }

        long trailingBytes = chunkSize - dataBytesToDecode;
        if (trailingBytes > 0L) {
            skipFully(inputStream, trailingBytes);
        }

        int sampleRate = Math.max(8_000, formatInfo.sampleRate());
        int stemCount = Math.max(1, Math.min(MAX_STEMS, formatInfo.channels() / 2));
        return new ModernWavTrack(sourceId, sampleRate, formatInfo.channels(), stemCount, frameCount, samples);
    }

    private static short decodeSampleToPcm16(
            byte[] buffer,
            int offset,
            int bytesPerSample,
            int bitsPerSample,
            int formatTag
    ) {
        if (offset < 0 || offset + bytesPerSample > buffer.length) {
            return 0;
        }

        if (formatTag == WAV_FORMAT_IEEE_FLOAT) {
            if (bytesPerSample >= 8) {
                long bits = int64LE(buffer, offset);
                double value = Double.longBitsToDouble(bits);
                return normalizedToPcm16(value);
            }
            if (bytesPerSample >= 4) {
                int bits = int32LE(buffer, offset);
                float value = Float.intBitsToFloat(bits);
                return normalizedToPcm16(value);
            }
            return 0;
        }

        int storedBits = Math.max(1, Math.min(32, bitsPerSample > 0 ? bitsPerSample : bytesPerSample * 8));
        int maxBitsFromBytes = Math.min(32, bytesPerSample * 8);
        if (maxBitsFromBytes <= 0) {
            return 0;
        }
        storedBits = Math.min(storedBits, maxBitsFromBytes);

        if (storedBits <= 8) {
            int unsigned = buffer[offset] & 0xFF;
            int centered = unsigned - 128;
            return (short) (centered << 8);
        }

        int value = 0;
        int bytesToRead = Math.min(bytesPerSample, 4);
        for (int index = 0; index < bytesToRead; index++) {
            value |= (buffer[offset + index] & 0xFF) << (index * 8);
        }

        int shiftForSign = 32 - storedBits;
        int signed = shiftForSign >= 0 ? (value << shiftForSign) >> shiftForSign : value;
        int pcm16;
        if (storedBits > 16) {
            pcm16 = signed >> (storedBits - 16);
        } else if (storedBits < 16) {
            pcm16 = signed << (16 - storedBits);
        } else {
            pcm16 = signed;
        }
        if (pcm16 > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (pcm16 < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) pcm16;
    }

    private static short normalizedToPcm16(double value) {
        double clamped = Math.max(-1.0D, Math.min(1.0D, value));
        return (short) Math.round(clamped * Short.MAX_VALUE);
    }

    private static int readFully(InputStream inputStream, byte[] target, int offset, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = inputStream.read(target, offset + totalRead, length - totalRead);
            if (read < 0) {
                break;
            }
            totalRead += read;
        }
        return totalRead;
    }

    private static void skipFully(InputStream inputStream, long bytes) throws IOException {
        long remaining = Math.max(0L, bytes);
        while (remaining > 0L) {
            long skipped = inputStream.skip(remaining);
            if (skipped <= 0L) {
                if (inputStream.read() < 0) {
                    break;
                }
                remaining--;
                continue;
            }
            remaining -= skipped;
        }
    }

    private static int uint16LE(byte[] source, int offset) {
        if (offset < 0 || offset + 2 > source.length) {
            return 0;
        }
        return (source[offset] & 0xFF) | ((source[offset + 1] & 0xFF) << 8);
    }

    private static long uint32LE(byte[] source, int offset) {
        if (offset < 0 || offset + 4 > source.length) {
            return 0L;
        }
        return ((long) source[offset] & 0xFFL)
                | (((long) source[offset + 1] & 0xFFL) << 8)
                | (((long) source[offset + 2] & 0xFFL) << 16)
                | (((long) source[offset + 3] & 0xFFL) << 24);
    }

    private static int int32LE(byte[] source, int offset) {
        return (int) uint32LE(source, offset);
    }

    private static long int64LE(byte[] source, int offset) {
        if (offset < 0 || offset + 8 > source.length) {
            return 0L;
        }
        return ((long) source[offset] & 0xFFL)
                | (((long) source[offset + 1] & 0xFFL) << 8)
                | (((long) source[offset + 2] & 0xFFL) << 16)
                | (((long) source[offset + 3] & 0xFFL) << 24)
                | (((long) source[offset + 4] & 0xFFL) << 32)
                | (((long) source[offset + 5] & 0xFFL) << 40)
                | (((long) source[offset + 6] & 0xFFL) << 48)
                | (((long) source[offset + 7] & 0xFFL) << 56);
    }

    private static String ascii(byte[] source, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > source.length) {
            return "";
        }
        return new String(source, offset, length, StandardCharsets.US_ASCII);
    }

    private static boolean equalsAscii(byte[] source, int offset, String expected) {
        if (expected == null || offset < 0 || offset + expected.length() > source.length) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if ((char) source[offset + index] != expected.charAt(index)) {
                return false;
            }
        }
        return true;
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

    private static void applyGain(float[] buffer, int sampleCount, double gain) {
        if (Math.abs(gain - 1.0D) < 0.000001D) {
            return;
        }
        for (int sample = 0; sample < sampleCount; sample++) {
            buffer[sample] = (float) (buffer[sample] * gain);
        }
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

    private record WavFormatInfo(
            int formatTag,
            int channels,
            int sampleRate,
            int bitsPerSample,
            int bytesPerSample,
            int blockAlign
    ) {
    }

    private record ModernWavTrack(
            ResourceLocation resourceId,
            int sampleRate,
            int channels,
            int stemCount,
            int frameCount,
            short[] samples
    ) {
        void copyStemStereo(long frameIndex, int stemTrack, float[] target, int targetOffset) {
            int clampedStem = Math.max(0, Math.min(stemCount - 1, stemTrack));
            int pairChannel = clampedStem * 2;
            int frame = (int) (frameIndex % Math.max(1, frameCount));
            int base = frame * channels;
            int leftChannel = pairChannel;
            int rightChannel = Math.min(pairChannel + 1, channels - 1);
            target[targetOffset] = samples[base + leftChannel] / 32768.0f;
            target[targetOffset + 1] = samples[base + rightChannel] / 32768.0f;
        }
    }

    public record ModernPlaybackSnapshot(
            String trackId,
            int stemTrack,
            int stemPairs,
            boolean supportsStemSwitching,
            boolean playing,
            long timelineMillis
    ) {
        public static ModernPlaybackSnapshot stopped() {
            return new ModernPlaybackSnapshot("", -1, 0, false, false, 0L);
        }
    }
}
