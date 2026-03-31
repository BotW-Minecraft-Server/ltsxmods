package link.botwmcs.core.api.fizzier.proxy;

import java.util.Set;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.core.api.fizzier.gui.IFizzyGuiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

public interface IFizzyProxyService {
    LtsxKernelUiSpecBuilder uiSpecBuilder();

    LtsxKernelAttachSpec emptyAttachSpec();

    LtsxKernelAttachSpec defaultAttachSpec();

    LtsxKernelAttachSpec attachSpec(
            LtsxKernelUiSpec uiSpec,
            LtsxPhaseBridgePolicy phasePolicy,
            LtsxTooltipPolicy tooltipPolicy,
            LtsxInputDispatchPolicy inputDispatchPolicy
    );

    void registerRule(LtsxProxyRule rule);

    void unregisterRule(ResourceLocation ruleId);

    void clearRules();

    int ruleCount();

    interface LtsxKernelUiSpec {
    }

    interface LtsxKernelUiSpecBuilder {
        LtsxKernelUiSpecBuilder baseGui(IFizzyGuiService.LtsxGui gui);

        LtsxKernelUiSpecBuilder frame(IFizzyGuiService.LtsxFrame frame);

        LtsxKernelUiSpecBuilder background(IFizzyGuiService.LtsxBackground background);

        LtsxKernelUiSpecBuilder behind(IFizzyGuiService.LtsxBehind behind);

        LtsxKernelUiSpecBuilder below(IFizzyElementService.LtsxElement below);

        LtsxKernelUiSpecBuilder overrideSizePx(Integer widthPx, Integer heightPx);

        LtsxKernelUiSpec build();
    }

    interface LtsxKernelAttachSpec {
        boolean isEmpty();

        LtsxTooltipPolicy tooltipPolicy();

        LtsxInputDispatchPolicy inputDispatchPolicy();
    }

    record LtsxInputDispatchPolicy(
            boolean overlayFirst,
            boolean cancelSourceWhenHandled,
            boolean blockSourceWhenHitBlockingElement
    ) {
        public static LtsxInputDispatchPolicy defaults() {
            return new LtsxInputDispatchPolicy(true, true, true);
        }
    }

    interface LtsxPhaseBridgePolicy {
        LtsxHostRenderStage map(LtsxUiRenderPhase phase, Set<LtsxHostRenderStage> supportedStages);
    }

    interface LtsxProxyRule {
        ResourceLocation id();

        int priority();

        boolean matches(LtsxProxyBuildContext context);

        LtsxKernelAttachSpec build(LtsxProxyBuildContext context);
    }

    record LtsxProxyBuildContext(
            Minecraft minecraft,
            Screen screen,
            String sourceModId
    ) {
    }

    enum LtsxTooltipPolicy {
        SOURCE_ONLY,
        FIZZY_ONLY,
        BOTH,
        AUTO_SUPPRESS_SOURCE_WHEN_BLOCKING
    }

    enum LtsxHostRenderStage {
        SCREEN_PRE,
        SOURCE_BG_PRE,
        SOURCE_BG_POST,
        SOURCE_CONTENT_PRE,
        SOURCE_CONTENT_POST,
        SOURCE_TOOLTIP_PRE,
        SOURCE_TOOLTIP_POST,
        SCREEN_POST
    }

    enum LtsxUiRenderPhase {
        BEHIND,
        BACKGROUND,
        FRAME,
        ELEMENT,
        SPLIT,
        WIDGET,
        TOOLTIP,
        OVERLAY
    }
}
