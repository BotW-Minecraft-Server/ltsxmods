package link.botwmcs.ltsxassistant.client.screen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.fizzy.client.elements.VanillaLikeAbstractButton;
import link.botwmcs.fizzy.client.util.FizzyGuiUtils;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.ui.background.BgPainter;
import link.botwmcs.fizzy.ui.behind.VanillaBehind;
import link.botwmcs.fizzy.ui.core.FizzyGui;
import link.botwmcs.fizzy.ui.core.FizzyGuiBuilder;
import link.botwmcs.fizzy.ui.core.HostType;
import link.botwmcs.fizzy.ui.element.ElementPainter;
import link.botwmcs.fizzy.ui.element.ElementType;
import link.botwmcs.fizzy.ui.element.button.VanillaLikeButtonElement;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.frame.FrameMetrics;
import link.botwmcs.fizzy.ui.frame.FramePainter;
import link.botwmcs.fizzy.ui.host.FizzyScreenHost;
import link.botwmcs.ltsxassistant.Config;
import link.botwmcs.ltsxassistant.api.soundengine.MusicAlbumApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicAlbumDescriptor;
import link.botwmcs.ltsxassistant.api.soundengine.MusicCoverApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicPlaybackApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicTrackDescriptor;
import link.botwmcs.ltsxassistant.api.soundengine.NowPlayingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Music control screen hosted by FizzyScreenHost.
 */
public final class MusicPlayerScreen extends FizzyScreenHost {
    private static final Component TITLE = Component.translatable("screen.ltsxassistant.music_player.title");
    private static final int PANEL_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int HALF_BUTTON_WIDTH = 96;
    private static final int COVER_SIZE = 48;
    private static final int TRACK_LIST_HEIGHT = 120;
    private static final int COVER_BG_COLOR = 0xFF30343F;
    private static final int TEXT_MAIN_COLOR = 0xFFE5E7EB;
    private static final int TEXT_SUB_COLOR = 0xFFBFC2CE;
    private final Screen parent;

    public MusicPlayerScreen(Screen parent) {
        this(parent, currentWidth(), currentHeight());
    }

    private MusicPlayerScreen(Screen parent, int width, int height) {
        super(buildGui(parent, width, height));
        this.parent = parent;
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        minecraft.setScreen(new MusicPlayerScreen(parent, width, height));
    }

    @Override
    public void onClose() {
        openParentScreen(parent);
    }

    @Override
    public Component getNarrationMessage() {
        return TITLE;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static FizzyGui buildGui(Screen parent, int width, int height) {
        FizzyGuiBuilder builder = FizzyGuiBuilder.start()
                .sizeSlots(1, 1)
                .host(HostType.SCREEN)
                .frame(new ViewportFramePainter())
                .background(EmptyBackgroundPainter.INSTANCE)
                .behind(new VanillaBehind())
                .overrideSizePx(width, height);

        int centerX = width / 2;
        int topY = Math.max(34, height / 2 - 120);
        int panelX = centerX - PANEL_WIDTH / 2;

        builder.padByPx(panelX, 20, PANEL_WIDTH, 14)
                .element(new FizzyComponentElement.Builder()
                        .addText(TITLE)
                        .align(TextRenderer.Align.CENTER)
                        .shadow(true)
                        .wrap(false)
                        .autoEllipsis(true)
                        .build())
                .done();

        builder.padByPx(panelX, topY, PANEL_WIDTH, BUTTON_HEIGHT)
                .element(buildModeButton(parent))
                .done();
        builder.padByPx(panelX, topY + 24, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
                .element(buildToggleButton())
                .done();
        builder.padByPx(panelX + HALF_BUTTON_WIDTH + 8, topY + 24, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
                .element(buildStopButton())
                .done();
        builder.padByPx(panelX, topY + 48, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
                .element(buildStemPrevButton())
                .done();
        builder.padByPx(panelX + HALF_BUTTON_WIDTH + 8, topY + 48, HALF_BUTTON_WIDTH, BUTTON_HEIGHT)
                .element(buildStemNextButton())
                .done();

        int listY = topY + 72;
        int doneY = listY + TRACK_LIST_HEIGHT + 4;
        int infoY = doneY + 24;

        builder.padByPx(panelX, listY, PANEL_WIDTH, TRACK_LIST_HEIGHT)
                .element(new AlbumTrackListElement())
                .done();

        builder.padByPx(panelX, doneY, PANEL_WIDTH, BUTTON_HEIGHT)
                .element(buildDoneButton(parent))
                .done();

        builder.padByPx(panelX, infoY, PANEL_WIDTH, COVER_SIZE)
                .element(new NowPlayingInfoElement())
                .done();
        return builder.build();
    }

    private static ElementPainter buildModeButton(Screen parent) {
        return VanillaLikeButtonElement.builder(button -> {
                    Config.ClientMusicEngineMode next = Config.musicEngineMode() == Config.ClientMusicEngineMode.CLASSIC
                            ? Config.ClientMusicEngineMode.MODERN
                            : Config.ClientMusicEngineMode.CLASSIC;
                    Config.setMusicEngineMode(next);
                    Minecraft.getInstance().setScreen(new MusicPlayerScreen(parent));
                })
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                .text(modeButtonLabel())
                .tooltip(Component.translatable("screen.ltsxassistant.music_player.entry.tooltip"))
                .narration(Component.translatable("screen.ltsxassistant.music_player.entry.narration"))
                .build();
    }

    private static ElementPainter buildToggleButton() {
        return VanillaLikeButtonElement.builder(button -> {
                    if (isClassicReadOnly()) {
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
                })
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                .text(Component.translatable("screen.ltsxassistant.music_player.toggle"))
                .build();
    }

    private static ElementPainter buildStopButton() {
        return VanillaLikeButtonElement.builder(button -> {
                    if (isClassicReadOnly()) {
                        return;
                    }
                    CoreServices.getOptional(MusicPlaybackApi.class).ifPresent(MusicPlaybackApi::stop);
                })
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                .text(Component.translatable("screen.ltsxassistant.music_player.stop"))
                .build();
    }

    private static ElementPainter buildStemPrevButton() {
        return VanillaLikeButtonElement.builder(button -> {
                    if (isClassicReadOnly()) {
                        return;
                    }
                    CoreServices.getOptional(MusicPlaybackApi.class).ifPresent(api -> {
                        NowPlayingSnapshot snapshot = api.nowPlaying();
                        api.setStemTrack(Math.max(0, snapshot.stemTrack() - 1));
                    });
                })
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                .text(Component.translatable("screen.ltsxassistant.music_player.stem_prev"))
                .build();
    }

    private static ElementPainter buildStemNextButton() {
        return VanillaLikeButtonElement.builder(button -> {
                    if (isClassicReadOnly()) {
                        return;
                    }
                    CoreServices.getOptional(MusicPlaybackApi.class).ifPresent(api -> {
                        NowPlayingSnapshot snapshot = api.nowPlaying();
                        api.setStemTrack(snapshot.stemTrack() + 1);
                    });
                })
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                .text(Component.translatable("screen.ltsxassistant.music_player.stem_next"))
                .build();
    }

    private static ElementPainter buildDoneButton(Screen parent) {
        return VanillaLikeButtonElement.builder(button -> openParentScreen(parent))
                .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                .text(Component.translatable("gui.done"))
                .build();
    }

    private static Component modeButtonLabel() {
        return Component.translatable(
                Config.musicEngineMode() == Config.ClientMusicEngineMode.CLASSIC
                        ? "screen.ltsxassistant.music_player.mode.classic"
                        : "screen.ltsxassistant.music_player.mode.modern"
        );
    }

    private static void openParentScreen(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (parent instanceof SoundOptionsScreen soundOptionsScreen) {
            Screen rebuilt = rebuildSoundOptionsScreen(soundOptionsScreen);
            if (rebuilt != null) {
                minecraft.setScreen(rebuilt);
                return;
            }
        }
        minecraft.setScreen(parent);
    }

    private static Screen rebuildSoundOptionsScreen(SoundOptionsScreen source) {
        Screen last = readField(source, "lastScreen", Screen.class);
        Options options = readField(source, "options", Options.class);
        if (last != null && options != null) {
            return new SoundOptionsScreen(last, options);
        }
        return null;
    }

    private static <T> T readField(Object target, String fieldName, Class<T> type) {
        if (target == null) {
            return null;
        }
        Class<?> cursor = target.getClass();
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                if (type.isInstance(value)) {
                    return type.cast(value);
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isClassicReadOnly() {
        return Config.musicEngineMode() == Config.ClientMusicEngineMode.CLASSIC;
    }

    private static int currentWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int currentHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = minecraft.font.width(ellipsis);
        String current = text;
        while (!current.isEmpty() && minecraft.font.width(current) + ellipsisWidth > maxWidth) {
            current = current.substring(0, current.length() - 1);
        }
        return current + ellipsis;
    }

    private static final class AlbumTrackListElement implements ElementPainter {
        private static final int BACKGROUND_COLOR = 0xB0171820;
        private static final int ROW_COLOR = 0x6630343F;
        private static final int ROW_COUNT = 6;
        private static final int ROW_HEIGHT = 14;

        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final List<Button> trackButtons = new ArrayList<>();
        private final String[] visibleTrackIds = new String[ROW_COUNT];

        private Button albumPrevButton;
        private Button albumNextButton;
        private Button pagePrevButton;
        private Button pageNextButton;
        private int pageIndex;

        @Override
        public void init(InitContext context, int x, int y, int width, int height) {
            widgets.clear();
            trackButtons.clear();
            albumPrevButton = context.addRenderableWidget(Button.builder(Component.literal("<A"), button -> switchAlbum(-1)).bounds(0, 0, 24, 16).build());
            albumNextButton = context.addRenderableWidget(Button.builder(Component.literal("A>"), button -> switchAlbum(1)).bounds(0, 0, 24, 16).build());
            pagePrevButton = context.addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1)).bounds(0, 0, 18, 16).build());
            pageNextButton = context.addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1)).bounds(0, 0, 18, 16).build());

            widgets.add(albumPrevButton);
            widgets.add(albumNextButton);
            widgets.add(pagePrevButton);
            widgets.add(pageNextButton);

            for (int index = 0; index < ROW_COUNT; index++) {
                final int rowIndex = index;
                Button rowButton = context.addRenderableWidget(Button.builder(Component.literal(""), button -> playRow(rowIndex)).bounds(0, 0, 100, ROW_HEIGHT).build());
                trackButtons.add(rowButton);
                widgets.add(rowButton);
            }
            syncLayout(x, y, width, height);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
            syncLayout(x, y, width, height);
            guiGraphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);

            MusicAlbumApi albumApi = CoreServices.getOptional(MusicAlbumApi.class).orElse(null);
            if (albumApi == null) {
                guiGraphics.drawString(Minecraft.getInstance().font, "Album API unavailable", x + 4, y + 4, TEXT_SUB_COLOR, false);
                setAllRowsInactive();
                return;
            }

            List<MusicAlbumDescriptor> albums = albumApi.albums();
            if (albums.isEmpty()) {
                guiGraphics.drawString(Minecraft.getInstance().font, "No album packs found", x + 4, y + 4, TEXT_SUB_COLOR, false);
                setAllRowsInactive();
                return;
            }

            String activeAlbumId = albumApi.selectedAlbumId();
            MusicAlbumDescriptor activeAlbum = albums.get(0);
            for (MusicAlbumDescriptor descriptor : albums) {
                if (descriptor.albumId().equals(activeAlbumId)) {
                    activeAlbum = descriptor;
                    break;
                }
            }

            List<MusicTrackDescriptor> tracks = albumApi.tracks(activeAlbum.albumId());
            int totalPages = Math.max(1, (tracks.size() + ROW_COUNT - 1) / ROW_COUNT);
            pageIndex = Math.max(0, Math.min(pageIndex, totalPages - 1));

            String albumLabel = trimToWidth(activeAlbum.displayName(), Math.max(20, width - 72));
            guiGraphics.drawString(Minecraft.getInstance().font, albumLabel, x + 30, y + 5, TEXT_MAIN_COLOR, false);
            guiGraphics.drawString(Minecraft.getInstance().font, "p" + (pageIndex + 1) + "/" + totalPages, x + width - 34, y + 5, TEXT_SUB_COLOR, false);

            int offset = pageIndex * ROW_COUNT;
            String currentTrackId = CoreServices.getOptional(MusicPlaybackApi.class).map(api -> api.nowPlaying().trackId()).orElse("");
            for (int row = 0; row < ROW_COUNT; row++) {
                int trackIndex = offset + row;
                Button rowButton = trackButtons.get(row);
                if (trackIndex < tracks.size()) {
                    MusicTrackDescriptor track = tracks.get(trackIndex);
                    visibleTrackIds[row] = track.trackId();
                    String prefix = track.trackNumber() > 0 ? String.format("%02d ", track.trackNumber()) : "-- ";
                    String title = trimToWidth(prefix + track.displayName(), Math.max(20, width - 14));
                    if (track.trackId().equals(currentTrackId)) {
                        title = "> " + trimToWidth(prefix + track.displayName(), Math.max(20, width - 18));
                    }
                    rowButton.setMessage(Component.literal(title));
                    rowButton.visible = true;
                    rowButton.active = !isClassicReadOnly();
                    guiGraphics.fill(x + 2, y + 22 + row * (ROW_HEIGHT + 2), x + width - 2, y + 22 + row * (ROW_HEIGHT + 2) + ROW_HEIGHT, ROW_COLOR);
                } else {
                    visibleTrackIds[row] = "";
                    rowButton.visible = false;
                    rowButton.active = false;
                }
            }

            boolean interactive = !isClassicReadOnly();
            albumPrevButton.active = interactive && albums.size() > 1;
            albumNextButton.active = interactive && albums.size() > 1;
            pagePrevButton.active = interactive && pageIndex > 0;
            pageNextButton.active = interactive && pageIndex + 1 < totalPages;
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
            if (albumPrevButton == null || albumNextButton == null || pagePrevButton == null || pageNextButton == null) {
                return;
            }
            FizzyGuiUtils.syncWidgetBounds(albumPrevButton, x + 2, y + 2, 24, 16);
            FizzyGuiUtils.syncWidgetBounds(albumNextButton, x + width - 64, y + 2, 24, 16);
            FizzyGuiUtils.syncWidgetBounds(pagePrevButton, x + width - 38, y + 2, 18, 16);
            FizzyGuiUtils.syncWidgetBounds(pageNextButton, x + width - 20, y + 2, 18, 16);

            int rowY = y + 22;
            for (int row = 0; row < trackButtons.size(); row++) {
                Button button = trackButtons.get(row);
                FizzyGuiUtils.syncWidgetBounds(button, x + 2, rowY + row * (ROW_HEIGHT + 2), Math.max(10, width - 4), ROW_HEIGHT);
            }
        }

        private void switchAlbum(int delta) {
            if (isClassicReadOnly()) {
                return;
            }
            MusicAlbumApi albumApi = CoreServices.getOptional(MusicAlbumApi.class).orElse(null);
            if (albumApi == null) {
                return;
            }
            List<MusicAlbumDescriptor> albums = albumApi.albums();
            if (albums.isEmpty()) {
                return;
            }
            String selected = albumApi.selectedAlbumId();
            int index = 0;
            for (int i = 0; i < albums.size(); i++) {
                if (albums.get(i).albumId().equals(selected)) {
                    index = i;
                    break;
                }
            }
            int next = Math.floorMod(index + delta, albums.size());
            albumApi.selectAlbum(albums.get(next).albumId());
            pageIndex = 0;
        }

        private void changePage(int delta) {
            pageIndex = Math.max(0, pageIndex + delta);
        }

        private void playRow(int rowIndex) {
            if (isClassicReadOnly()) {
                return;
            }
            if (rowIndex < 0 || rowIndex >= visibleTrackIds.length) {
                return;
            }
            String trackId = visibleTrackIds[rowIndex];
            if (trackId == null || trackId.isBlank()) {
                return;
            }
            MusicAlbumApi albumApi = CoreServices.getOptional(MusicAlbumApi.class).orElse(null);
            if (albumApi == null) {
                return;
            }
            String selectedAlbum = albumApi.selectedAlbumId();
            if (selectedAlbum.isBlank()) {
                return;
            }
            albumApi.playTrack(selectedAlbum, trackId);
        }

        private void setAllRowsInactive() {
            for (int row = 0; row < trackButtons.size(); row++) {
                Button rowButton = trackButtons.get(row);
                rowButton.visible = false;
                rowButton.active = false;
                visibleTrackIds[row] = "";
            }
            if (albumPrevButton != null) {
                albumPrevButton.active = false;
            }
            if (albumNextButton != null) {
                albumNextButton.active = false;
            }
            if (pagePrevButton != null) {
                pagePrevButton.active = false;
            }
            if (pageNextButton != null) {
                pageNextButton.active = false;
            }
        }
    }

    private static final class NowPlayingInfoElement implements ElementPainter {
        @Override
        public void render(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
            MusicPlaybackApi playbackApi = CoreServices.getOptional(MusicPlaybackApi.class).orElse(null);
            NowPlayingSnapshot snapshot = playbackApi == null ? NowPlayingSnapshot.stopped() : playbackApi.nowPlaying();
            ResourceLocation cover = CoreServices.getOptional(MusicCoverApi.class).map(MusicCoverApi::currentCoverTexture).orElse(null);

            int coverX = x;
            int coverY = y;
            guiGraphics.fill(coverX, coverY, coverX + COVER_SIZE, coverY + COVER_SIZE, COVER_BG_COLOR);
            if (cover != null) {
                guiGraphics.blit(cover, coverX, coverY, 0.0F, 0.0F, COVER_SIZE, COVER_SIZE, COVER_SIZE, COVER_SIZE);
            }
            int infoX = coverX + COVER_SIZE + 8;
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    "Track: " + (snapshot.trackId().isBlank() ? "-" : snapshot.trackId()),
                    infoX,
                    coverY + 4,
                    TEXT_MAIN_COLOR,
                    false
            );
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    "Album: " + (snapshot.albumId().isBlank() ? "-" : snapshot.albumId()),
                    infoX,
                    coverY + 16,
                    TEXT_SUB_COLOR,
                    false
            );
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    "Mode: " + snapshot.mode().serializedName() + "  Stem: " + snapshot.stemTrack(),
                    infoX,
                    coverY + 30,
                    TEXT_SUB_COLOR,
                    false
            );
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    "Time: " + formatTime(snapshot.timelineMillis()),
                    infoX,
                    coverY + 42,
                    TEXT_MAIN_COLOR,
                    false
            );
            if (isClassicReadOnly()) {
                guiGraphics.drawString(
                        Minecraft.getInstance().font,
                        "Classic mode: read-only",
                        x + width - 118,
                        coverY + 42,
                        TEXT_SUB_COLOR,
                        false
                );
            }
        }

        @Override
        public ElementType type() {
            return ElementType.CUSTOM;
        }

        @Override
        public List<AbstractWidget> widgets() {
            return List.of();
        }
    }

    private static final class ViewportFramePainter implements FramePainter {
        private static final FrameMetrics METRICS = new FrameMetrics() {
            @Override
            public int texW() {
                return 0;
            }

            @Override
            public int texH() {
                return 0;
            }

            @Override
            public int panelW() {
                return 0;
            }

            @Override
            public int titleStartH() {
                return 0;
            }

            @Override
            public int slotStartTopPx() {
                return 0;
            }

            @Override
            public int slotStartLeftPx() {
                return 0;
            }

            @Override
            public int slotSizePx() {
                return 0;
            }

            @Override
            public int slotInnerStartY() {
                return 0;
            }

            @Override
            public int slotInnerHeight() {
                return 0;
            }

            @Override
            public int topBorderY() {
                return 0;
            }

            @Override
            public int bottomBorderY() {
                return 0;
            }

            @Override
            public int bottomPadStartY() {
                return 0;
            }

            @Override
            public int bottomPadHeight() {
                return 0;
            }

            @Override
            public int bottomEdgeStartY() {
                return 0;
            }

            @Override
            public int bottomEdgeHeight() {
                return 0;
            }

            @Override
            public int buttomInvExtraStartY() {
                return 0;
            }

            @Override
            public int buttomInvExtraHeight() {
                return 0;
            }

            @Override
            public int totalHeightForRows(int rows, boolean screen, boolean below) {
                return 0;
            }

            @Override
            public int totalWidthForCols(int cols) {
                return 0;
            }
        };

        private Layout layout = new Layout(0, 0, 0, 0, false, false);

        @Override
        public void paint(GuiGraphics guiGraphics, int left, int top, int width, int height, boolean drawBottomEdge, boolean hasBelow) {
        }

        @Override
        public FrameMetrics metrics() {
            return METRICS;
        }

        @Override
        public void setLayout(int left, int top, int width, int height, boolean drawBottomEdge, boolean hasBelow) {
            layout = new Layout(left, top, width, height, drawBottomEdge, hasBelow);
        }

        @Override
        public Layout layout() {
            return layout;
        }

        @Override
        public SlotArea currentSlotArea() {
            return new SlotArea(layout.left(), layout.top(), layout.w(), layout.h());
        }

        @Override
        public BelowArea currentBelowArea() {
            return new BelowArea(layout.left(), layout.top(), layout.w(), layout.h());
        }
    }

    private enum EmptyBackgroundPainter implements BgPainter {
        INSTANCE;

        @Override
        public void paint(GuiGraphics guiGraphics, FramePainter framePainter) {
        }
    }
}