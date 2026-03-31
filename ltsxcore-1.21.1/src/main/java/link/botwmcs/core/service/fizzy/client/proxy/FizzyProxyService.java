package link.botwmcs.core.service.fizzy.client.proxy;

import java.util.Objects;

import link.botwmcs.core.api.fizzier.proxy.IFizzyProxyService;
import link.botwmcs.core.service.fizzy.client.bridge.FizzyClientBridges;
import link.botwmcs.fizzy.proxy.api.HostRenderStage;
import link.botwmcs.fizzy.proxy.api.InputDispatchPolicy;
import link.botwmcs.fizzy.proxy.api.KernelAttachSpec;
import link.botwmcs.fizzy.proxy.api.KernelUiSpec;
import link.botwmcs.fizzy.proxy.api.PhaseBridgePolicy;
import link.botwmcs.fizzy.proxy.api.TooltipPolicy;
import link.botwmcs.fizzy.proxy.rule.ProxyBuildContext;
import link.botwmcs.fizzy.proxy.rule.ProxyRule;
import link.botwmcs.fizzy.proxy.runtime.ScreenProxyRuntime;
import net.minecraft.resources.ResourceLocation;

public final class FizzyProxyService implements IFizzyProxyService {
    private final ScreenProxyRuntime runtime = ScreenProxyRuntime.instance();

    @Override
    public LtsxKernelUiSpecBuilder uiSpecBuilder() {
        return new KernelUiSpecBuilderBridge(KernelUiSpec.builder());
    }

    @Override
    public LtsxKernelAttachSpec emptyAttachSpec() {
        return new KernelAttachSpecBridge(KernelAttachSpec.empty());
    }

    @Override
    public LtsxKernelAttachSpec defaultAttachSpec() {
        return new KernelAttachSpecBridge(KernelAttachSpec.defaults());
    }

    @Override
    public LtsxKernelAttachSpec attachSpec(
            LtsxKernelUiSpec uiSpec,
            LtsxPhaseBridgePolicy phasePolicy,
            LtsxTooltipPolicy tooltipPolicy,
            LtsxInputDispatchPolicy inputDispatchPolicy
    ) {
        return new KernelAttachSpecBridge(new KernelAttachSpec(
                unwrapUiSpec(Objects.requireNonNull(uiSpec, "uiSpec")),
                adaptPhasePolicy(Objects.requireNonNull(phasePolicy, "phasePolicy")),
                mapTooltipPolicy(Objects.requireNonNull(tooltipPolicy, "tooltipPolicy")),
                new InputDispatchPolicy(
                        inputDispatchPolicy.overlayFirst(),
                        inputDispatchPolicy.cancelSourceWhenHandled(),
                        inputDispatchPolicy.blockSourceWhenHitBlockingElement()
                )
        ));
    }

    @Override
    public void registerRule(LtsxProxyRule rule) {
        runtime.ruleRegistry().register(new ProxyRuleBridge(Objects.requireNonNull(rule, "rule")));
    }

    @Override
    public void unregisterRule(ResourceLocation ruleId) {
        runtime.ruleRegistry().unregister(Objects.requireNonNull(ruleId, "ruleId"));
    }

    @Override
    public void clearRules() {
        runtime.ruleRegistry().clear();
    }

    @Override
    public int ruleCount() {
        return runtime.ruleRegistry().size();
    }

    static KernelUiSpec unwrapUiSpec(LtsxKernelUiSpec uiSpec) {
        return ((KernelUiSpecBridge) uiSpec).delegate;
    }

    private static KernelAttachSpec unwrapAttachSpec(LtsxKernelAttachSpec attachSpec) {
        return ((KernelAttachSpecBridge) attachSpec).delegate;
    }

    private static TooltipPolicy mapTooltipPolicy(LtsxTooltipPolicy tooltipPolicy) {
        return TooltipPolicy.valueOf(tooltipPolicy.name());
    }

    private static LtsxTooltipPolicy mapTooltipPolicy(TooltipPolicy tooltipPolicy) {
        return LtsxTooltipPolicy.valueOf(tooltipPolicy.name());
    }

    private static HostRenderStage mapStage(LtsxHostRenderStage stage) {
        return HostRenderStage.valueOf(stage.name());
    }

    private static LtsxHostRenderStage mapStage(HostRenderStage stage) {
        return LtsxHostRenderStage.valueOf(stage.name());
    }

    private static link.botwmcs.fizzy.ui.kernel.render.UiRenderPhase mapPhase(LtsxUiRenderPhase phase) {
        return link.botwmcs.fizzy.ui.kernel.render.UiRenderPhase.valueOf(phase.name());
    }

    private static LtsxUiRenderPhase mapPhase(link.botwmcs.fizzy.ui.kernel.render.UiRenderPhase phase) {
        return LtsxUiRenderPhase.valueOf(phase.name());
    }

    private static PhaseBridgePolicy adaptPhasePolicy(LtsxPhaseBridgePolicy phasePolicy) {
        return (phase, capabilities) -> mapStage(phasePolicy.map(
                mapPhase(phase),
                capabilities.stages().stream().map(FizzyProxyService::mapStage).collect(java.util.stream.Collectors.toSet())
        ));
    }

    private record KernelUiSpecBridge(KernelUiSpec delegate) implements LtsxKernelUiSpec {
    }

    private record KernelAttachSpecBridge(KernelAttachSpec delegate) implements LtsxKernelAttachSpec {
        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public LtsxTooltipPolicy tooltipPolicy() {
            return mapTooltipPolicy(delegate.tooltipPolicy());
        }

        @Override
        public LtsxInputDispatchPolicy inputDispatchPolicy() {
            InputDispatchPolicy inputPolicy = delegate.inputPolicy();
            return new LtsxInputDispatchPolicy(
                    inputPolicy.overlayFirst(),
                    inputPolicy.cancelSourceWhenHandled(),
                    inputPolicy.blockSourceWhenHitBlockingElement()
            );
        }
    }

    private static final class KernelUiSpecBuilderBridge implements LtsxKernelUiSpecBuilder {
        private final KernelUiSpec.Builder delegate;

        private KernelUiSpecBuilderBridge(KernelUiSpec.Builder delegate) {
            this.delegate = delegate;
        }

        @Override
        public LtsxKernelUiSpecBuilder baseGui(link.botwmcs.core.api.fizzier.gui.IFizzyGuiService.LtsxGui gui) {
            delegate.baseKernel(FizzyClientBridges.unwrapGui(Objects.requireNonNull(gui, "gui")));
            return this;
        }

        @Override
        public LtsxKernelUiSpecBuilder frame(link.botwmcs.core.api.fizzier.gui.IFizzyGuiService.LtsxFrame frame) {
            delegate.frame(FizzyClientBridges.unwrapFrame(Objects.requireNonNull(frame, "frame")));
            return this;
        }

        @Override
        public LtsxKernelUiSpecBuilder background(link.botwmcs.core.api.fizzier.gui.IFizzyGuiService.LtsxBackground background) {
            delegate.background(FizzyClientBridges.unwrapBackground(Objects.requireNonNull(background, "background")));
            return this;
        }

        @Override
        public LtsxKernelUiSpecBuilder behind(link.botwmcs.core.api.fizzier.gui.IFizzyGuiService.LtsxBehind behind) {
            delegate.behind(FizzyClientBridges.unwrapBehind(Objects.requireNonNull(behind, "behind")));
            return this;
        }

        @Override
        public LtsxKernelUiSpecBuilder below(link.botwmcs.core.api.fizzier.element.IFizzyElementService.LtsxElement below) {
            delegate.below(FizzyClientBridges.unwrapElement(Objects.requireNonNull(below, "below")));
            return this;
        }

        @Override
        public LtsxKernelUiSpecBuilder overrideSizePx(Integer widthPx, Integer heightPx) {
            delegate.overrideSizePx(widthPx, heightPx);
            return this;
        }

        @Override
        public LtsxKernelUiSpec build() {
            return new KernelUiSpecBridge(delegate.build());
        }
    }

    private static final class ProxyRuleBridge implements ProxyRule {
        private final LtsxProxyRule delegate;

        private ProxyRuleBridge(LtsxProxyRule delegate) {
            this.delegate = delegate;
        }

        @Override
        public ResourceLocation id() {
            return delegate.id();
        }

        @Override
        public int priority() {
            return delegate.priority();
        }

        @Override
        public boolean matches(ProxyBuildContext context) {
            return delegate.matches(adaptContext(context));
        }

        @Override
        public KernelAttachSpec build(ProxyBuildContext context) {
            return unwrapAttachSpec(delegate.build(adaptContext(context)));
        }

        private static LtsxProxyBuildContext adaptContext(ProxyBuildContext context) {
            return new LtsxProxyBuildContext(
                    context.minecraft(),
                    context.screen(),
                    context.sourceModId()
            );
        }
    }
}
