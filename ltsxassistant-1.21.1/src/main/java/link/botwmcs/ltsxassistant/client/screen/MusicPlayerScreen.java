package link.botwmcs.ltsxassistant.client.screen;

import java.lang.reflect.Field;
import java.util.List;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.fizzy.client.elements.VanillaLikeAbstractButton;
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
import link.botwmcs.ltsxassistant.api.soundengine.MusicCoverApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicPlaybackApi;
import link.botwmcs.ltsxassistant.api.soundengine.NowPlayingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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
        int topY = height / 2 - 70;
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
        builder.padByPx(panelX, topY + 126, PANEL_WIDTH, BUTTON_HEIGHT)
                .element(buildDoneButton(parent))
                .done();

        builder.padByPx(panelX, height / 2 + 6, PANEL_WIDTH, COVER_SIZE)
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
                    "Mode: " + snapshot.mode().serializedName(),
                    infoX,
                    coverY + 18,
                    TEXT_SUB_COLOR,
                    false
            );
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    "Stem: " + snapshot.stemTrack(),
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
