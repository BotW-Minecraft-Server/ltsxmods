package link.botwmcs.ltsxassistant.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.api.fizzier.contrib.IFizzyProxyRuleContributor;
import link.botwmcs.core.api.fizzier.proxy.IFizzyProxyService;
import link.botwmcs.fizzy.proxy.api.KernelAttachSpec;
import link.botwmcs.fizzy.proxy.api.KernelUiSpec;
import link.botwmcs.fizzy.proxy.api.TooltipPolicy;
import link.botwmcs.fizzy.proxy.rule.ProxyBuildContext;
import link.botwmcs.fizzy.proxy.rule.ProxyRule;
import link.botwmcs.fizzy.proxy.runtime.ScreenProxyRuntime;
import link.botwmcs.fizzy.ui.pad.PixelPadSpec;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.client.elements.MusicMiniPlayerElement;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.resources.ResourceLocation;

/**
 * Adds mini player to pause screen.
 */
public final class AssistantMusicScreenProxyContributor implements IFizzyProxyRuleContributor {
    private static final ResourceLocation PAUSE_RULE_ID =
            ResourceLocation.fromNamespaceAndPath(LTSXAssistant.MODID, "proxy/pause_music_mini_player");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    @Override
    public void contribute(IFizzyProxyService proxyService) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        var registry = ScreenProxyRuntime.instance().ruleRegistry();
        registry.unregister(PAUSE_RULE_ID);
        registry.register(new PauseMiniPlayerRule());
    }

    private static final class PauseMiniPlayerRule implements ProxyRule {
        private static final int PLAYER_WIDTH = 220;
        private static final int PLAYER_HEIGHT = 56;
        private static final int BOTTOM_MARGIN = 8;

        @Override
        public ResourceLocation id() {
            return PAUSE_RULE_ID;
        }

        @Override
        public int priority() {
            return 315;
        }

        @Override
        public boolean matches(ProxyBuildContext context) {
            return context.screen() instanceof PauseScreen;
        }

        @Override
        public KernelAttachSpec build(ProxyBuildContext context) {
            PauseScreen screen = (PauseScreen) context.screen();
            int rootWidth = context.geometry().rootWidth();
            int rootHeight = context.geometry().rootHeight();

            int x = Math.max(0, (rootWidth - PLAYER_WIDTH) / 2);
            int y = resolveMiniPlayerY(screen, rootHeight);

            KernelUiSpec ui = KernelUiSpec.builder()
                    .addPad(new PixelPadSpec(
                            x,
                            y,
                            PLAYER_WIDTH,
                            PLAYER_HEIGHT,
                            List.of(new MusicMiniPlayerElement())
                    ))
                    .build();
            return new KernelAttachSpec(ui, null, TooltipPolicy.BOTH, null);
        }

        private static int resolveMiniPlayerY(PauseScreen screen, int rootHeight) {
            int bottom = 0;
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible) {
                    bottom = Math.max(bottom, widget.getY() + widget.getHeight());
                }
            }
            if (bottom <= 0) {
                return Math.max(0, rootHeight / 2 + 58);
            }
            return Math.min(Math.max(0, bottom + 6), Math.max(0, rootHeight - PLAYER_HEIGHT - BOTTOM_MARGIN));
        }
    }
}
