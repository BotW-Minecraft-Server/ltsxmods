package link.botwmcs.ltsxassistant.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.api.fizzier.contrib.IFizzyProxyRuleContributor;
import link.botwmcs.core.api.fizzier.proxy.IFizzyProxyService;
import link.botwmcs.fizzy.client.elements.VanillaLikeAbstractButton;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.proxy.api.KernelAttachSpec;
import link.botwmcs.fizzy.proxy.api.KernelUiSpec;
import link.botwmcs.fizzy.proxy.api.TooltipPolicy;
import link.botwmcs.fizzy.proxy.rule.ProxyBuildContext;
import link.botwmcs.fizzy.proxy.rule.ProxyRule;
import link.botwmcs.fizzy.proxy.runtime.ScreenProxyRuntime;
import link.botwmcs.fizzy.ui.element.button.VanillaLikeButtonElement;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.pad.PixelPadSpec;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.client.screen.MusicPlayerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Adds a dedicated Music Player button on SoundOptionsScreen, anchored near voice slider.
 */
public final class AssistantSoundOptionsMusicPlayerProxyContributor implements IFizzyProxyRuleContributor {
    private static final ResourceLocation RULE_ID =
            ResourceLocation.fromNamespaceAndPath(LTSXAssistant.MODID, "proxy/sound_options_music_player_entry");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    @Override
    public void contribute(IFizzyProxyService proxyService) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        var registry = ScreenProxyRuntime.instance().ruleRegistry();
        registry.unregister(RULE_ID);
        registry.register(new SoundOptionsMusicEntryRule());
    }

    private static final class SoundOptionsMusicEntryRule implements ProxyRule {
        private static final int BUTTON_WIDTH = 20;
        private static final int BUTTON_HEIGHT = 20;
        private static final int RIGHT_MARGIN = 8;
        private static final int TOP_MARGIN = 8;

        @Override
        public ResourceLocation id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            return 322;
        }

        @Override
        public boolean matches(ProxyBuildContext context) {
            return context.screen() != null && context.screen().getClass() == SoundOptionsScreen.class;
        }

        @Override
        public KernelAttachSpec build(ProxyBuildContext context) {
            Screen screen = context.screen();
            int rootWidth = context.geometry().rootWidth();
            int x = Math.max(0, rootWidth - RIGHT_MARGIN - BUTTON_WIDTH);
            int y = TOP_MARGIN;

            KernelUiSpec ui = KernelUiSpec.builder()
                    .addPad(new PixelPadSpec(
                            x,
                            y,
                            BUTTON_WIDTH,
                            BUTTON_HEIGHT,
                            List.of(createOpenMusicScreenButton(screen))
                    ))
                    .build();
            LTSXAssistant.LOGGER.debug(
                    "[ltsxassistant] Built sound options music entry for {} at ({}, {})",
                    screen == null ? "null" : screen.getClass().getName(),
                    x,
                    y
            );
            return new KernelAttachSpec(ui, null, TooltipPolicy.BOTH, null);
        }

        private static VanillaLikeButtonElement createOpenMusicScreenButton(Screen sourceScreen) {
            return VanillaLikeButtonElement.builder(button ->
                            Minecraft.getInstance().setScreen(new MusicPlayerScreen(sourceScreen)))
                    .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                    .text(new FizzyComponentElement.Builder()
                            .addText(Component.literal("M"))
                            .align(TextRenderer.Align.CENTER)
                            .shadow(true)
                            .wrap(false)
                            .autoEllipsis(true)
                            .build())
                    .tooltip(Component.translatable("screen.ltsxassistant.music_player.entry.tooltip"))
                    .narration(Component.translatable("screen.ltsxassistant.music_player.entry.narration"))
                    .build();
        }
    }
}
