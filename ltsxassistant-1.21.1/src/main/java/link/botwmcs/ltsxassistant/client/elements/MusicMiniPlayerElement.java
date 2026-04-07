package link.botwmcs.ltsxassistant.client.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.fizzy.client.util.FizzyGuiUtils;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import link.botwmcs.ltsxassistant.Config;
import link.botwmcs.ltsxassistant.api.soundengine.MusicCoverApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicPlaybackApi;
import link.botwmcs.ltsxassistant.api.soundengine.NowPlayingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Compact pause-screen mini player painter.
 */
public final class MusicMiniPlayerElement implements ElementPainter {
    private static final int OUTER_COLOR = 0xC214151A;
    private static final int INNER_COLOR = 0xC922242C;
    private static final int BORDER_LIGHT = 0xE54E515D;
    private static final int BORDER_DARK = 0xB807080C;
    private static final int COVER_FALLBACK_COLOR = 0xFF30343F;
    private static final int COVER_PADDING = 4;
    private static final Pattern TRACK_NUMBER_PATTERN = Pattern.compile("^(.*?)(\\d+)$");

    private final List<AbstractWidget> widgets = new ArrayList<>();
    private Button previousButton;
    private Button playPauseButton;
    private Button nextButton;

    @Override
    public void init(InitContext context, int x, int y, int width, int height) {
        widgets.clear();
        previousButton = context.addRenderableWidget(Button.builder(Component.literal("<<"), button -> cycleTrack(-1)).bounds(0, 0, 20, 20).build());
        playPauseButton = context.addRenderableWidget(Button.builder(Component.literal(">"), button -> togglePlayPause()).bounds(0, 0, 36, 20).build());
        nextButton = context.addRenderableWidget(Button.builder(Component.literal(">>"), button -> cycleTrack(1)).bounds(0, 0, 20, 20).build());
        widgets.add(previousButton);
        widgets.add(playPauseButton);
        widgets.add(nextButton);
        syncLayout(x, y, width, height);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        syncLayout(x, y, width, height);

        int x2 = x + width;
        int y2 = y + height;
        guiGraphics.fill(x, y, x2, y2, OUTER_COLOR);
        guiGraphics.fill(x + 1, y + 1, x2 - 1, y2 - 1, INNER_COLOR);
        guiGraphics.fill(x, y, x2, y + 1, BORDER_LIGHT);
        guiGraphics.fill(x, y2 - 1, x2, y2, BORDER_DARK);
        guiGraphics.fill(x, y, x + 1, y2, BORDER_LIGHT);
        guiGraphics.fill(x2 - 1, y, x2, y2, BORDER_DARK);

        MusicPlaybackApi playbackApi = CoreServices.getOptional(MusicPlaybackApi.class).orElse(null);
        NowPlayingSnapshot snapshot = playbackApi == null ? NowPlayingSnapshot.stopped() : playbackApi.nowPlaying();

        int coverSize = Math.max(16, height - COVER_PADDING * 2);
        int coverX = x + COVER_PADDING;
        int coverY = y + (height - coverSize) / 2;
        guiGraphics.fill(coverX, coverY, coverX + coverSize, coverY + coverSize, COVER_FALLBACK_COLOR);

        MusicCoverApi coverApi = CoreServices.getOptional(MusicCoverApi.class).orElse(null);
        if (coverApi != null) {
            ResourceLocation cover = coverApi.currentCoverTexture();
            if (cover != null) {
                guiGraphics.blit(cover, coverX, coverY, 0.0F, 0.0F, coverSize, coverSize, coverSize, coverSize);
            }
        }

        String trackText = snapshot.trackId().isBlank() ? "No track" : snapshot.trackId();
        String modeText = "Mode: " + snapshot.mode().serializedName();
        String timeText = formatTime(snapshot.timelineMillis());
        int contentX = coverX + coverSize + 8;
        int contentWidth = Math.max(1, x2 - contentX - 4);
        String trimmedTrack = trimToWidth(trackText, contentWidth);
        guiGraphics.drawString(Minecraft.getInstance().font, trimmedTrack, contentX, y + 5, 0xFFE6E7EB, false);
        guiGraphics.drawString(Minecraft.getInstance().font, modeText, contentX, y + height - 24, 0xFFB9BBC6, false);
        guiGraphics.drawString(Minecraft.getInstance().font, timeText, contentX, y + height - 13, 0xFFE6E7EB, false);

        playPauseButton.setMessage(Component.literal(snapshot.playing() ? "||" : ">"));
        boolean hasTrack = !snapshot.trackId().isBlank();
        boolean classicReadOnly = Config.musicEngineMode() == Config.ClientMusicEngineMode.CLASSIC;
        playPauseButton.active = !classicReadOnly;
        previousButton.active = !classicReadOnly && hasTrack;
        nextButton.active = !classicReadOnly && hasTrack;
    }

    @Override
    public ElementType type() {
        return ElementType.CUSTOM;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return widgets;
    }

    private void syncLayout(int x, int y, int width, int height) {
        if (previousButton == null || playPauseButton == null || nextButton == null) {
            return;
        }
        int coverSize = Math.max(16, height - COVER_PADDING * 2);
        int controlsX = x + COVER_PADDING + coverSize + 8;
        int controlsY = y + 5 + 10;
        FizzyGuiUtils.syncWidgetBounds(previousButton, controlsX, controlsY, 20, 20);
        FizzyGuiUtils.syncWidgetBounds(playPauseButton, controlsX + 24, controlsY, 36, 20);
        FizzyGuiUtils.syncWidgetBounds(nextButton, controlsX + 64, controlsY, 20, 20);
    }

    private static void togglePlayPause() {
        if (Config.musicEngineMode() == Config.ClientMusicEngineMode.CLASSIC) {
            return;
        }
        CoreServices.getOptional(MusicPlaybackApi.class).ifPresent(api -> {
            NowPlayingSnapshot snapshot = api.nowPlaying();
            if (snapshot.playing()) {
                api.pause();
            } else {
                api.resume();
            }
        });
    }

    private static void cycleTrack(int delta) {
        if (Config.musicEngineMode() == Config.ClientMusicEngineMode.CLASSIC) {
            return;
        }
        CoreServices.getOptional(MusicPlaybackApi.class).ifPresent(api -> {
            NowPlayingSnapshot snapshot = api.nowPlaying();
            if (snapshot.trackId().isBlank()) {
                return;
            }
            String cycled = cycleTrackId(snapshot.trackId(), delta);
            if (cycled.equals(snapshot.trackId())) {
                return;
            }
            api.setTrack(snapshot.mode(), snapshot.albumId(), cycled);
            api.resume();
        });
    }

    private static String cycleTrackId(String rawTrackId, int delta) {
        int colon = rawTrackId.indexOf(':');
        String namespace = colon >= 0 ? rawTrackId.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? rawTrackId.substring(colon + 1) : rawTrackId;
        int slash = path.lastIndexOf('/');
        String directory = slash >= 0 ? path.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        String extension = "";
        if (fileName.endsWith(".ogg")) {
            extension = ".ogg";
            fileName = fileName.substring(0, fileName.length() - 4);
        } else if (fileName.endsWith(".wav")) {
            extension = ".wav";
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        Matcher matcher = TRACK_NUMBER_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return rawTrackId;
        }
        String prefix = matcher.group(1);
        String numberRaw = matcher.group(2);
        int number;
        try {
            number = Integer.parseInt(numberRaw);
        } catch (NumberFormatException ignored) {
            return rawTrackId;
        }
        int next = Math.max(1, number + delta);
        String padded = String.format("%0" + numberRaw.length() + "d", next);
        return namespace + ":" + directory + prefix + padded + extension;
    }

    private static String formatTime(long timelineMillis) {
        long totalSeconds = Math.max(0L, timelineMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static String trimToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (Minecraft.getInstance().font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = Minecraft.getInstance().font.width(ellipsis);
        String current = text;
        while (!current.isEmpty() && Minecraft.getInstance().font.width(current) + ellipsisWidth > maxWidth) {
            current = current.substring(0, current.length() - 1);
        }
        return current + ellipsis;
    }
}
