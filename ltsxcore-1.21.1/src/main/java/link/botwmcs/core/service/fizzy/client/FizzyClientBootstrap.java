package link.botwmcs.core.service.fizzy.client;

import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.api.fizzier.contrib.IFizzyEmojiContributor;
import link.botwmcs.core.api.fizzier.contrib.IFizzyInlineImageContributor;
import link.botwmcs.core.api.fizzier.contrib.IFizzyPlaceholderContributor;
import link.botwmcs.core.api.fizzier.contrib.IFizzyProxyRuleContributor;
import link.botwmcs.core.api.fizzier.element.IFizzyElementService;
import link.botwmcs.core.api.fizzier.gui.IFizzyGuiService;
import link.botwmcs.core.api.fizzier.overlay.IFizzyOverlayClientService;
import link.botwmcs.core.api.fizzier.proxy.IFizzyProxyService;
import link.botwmcs.core.api.fizzier.reactive.IFizzyReactiveService;
import link.botwmcs.core.api.fizzier.text.IFizzyTextService;
import link.botwmcs.core.service.fizzy.client.element.FizzyElementService;
import link.botwmcs.core.service.fizzy.client.gui.FizzyGuiService;
import link.botwmcs.core.service.fizzy.client.overlay.FizzyOverlayClientService;
import link.botwmcs.core.service.fizzy.client.proxy.FizzyProxyService;
import link.botwmcs.core.service.fizzy.client.reactive.FizzyReactiveService;
import link.botwmcs.core.service.fizzy.client.text.FizzyTextService;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.core.util.CoreKeys;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;

public final class FizzyClientBootstrap {
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    private FizzyClientBootstrap() {
    }

    public static void bootstrap(Logger logger, IEventBus modBus) {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }

        CoreServices.registerIfAbsent(IFizzyTextService.class, new FizzyTextService());
        CoreServices.registerIfAbsent(IFizzyElementService.class, new FizzyElementService());
        CoreServices.registerIfAbsent(IFizzyReactiveService.class, new FizzyReactiveService());
        CoreServices.registerIfAbsent(IFizzyGuiService.class, new FizzyGuiService());
        CoreServices.registerIfAbsent(IFizzyOverlayClientService.class, new FizzyOverlayClientService());
        CoreServices.registerIfAbsent(IFizzyProxyService.class, new FizzyProxyService());

        logger.info("{}Fizzy client services registered.", CoreKeys.LOG_PREFIX);
    }

    public static void applyContributors(Logger logger) {
        IFizzyTextService textService = CoreServices.get(IFizzyTextService.class);
        IFizzyProxyService proxyService = CoreServices.get(IFizzyProxyService.class);

        for (IFizzyEmojiContributor contributor : CoreServices.getMulti(IFizzyEmojiContributor.class)) {
            contributor.contribute(textService);
        }
        for (IFizzyInlineImageContributor contributor : CoreServices.getMulti(IFizzyInlineImageContributor.class)) {
            contributor.contribute(textService);
        }
        for (IFizzyPlaceholderContributor contributor : CoreServices.getMulti(IFizzyPlaceholderContributor.class)) {
            contributor.contribute(textService);
        }
        for (IFizzyProxyRuleContributor contributor : CoreServices.getMulti(IFizzyProxyRuleContributor.class)) {
            contributor.contribute(proxyService);
        }

        logger.info("{}Applied Fizzy client contributors.", CoreKeys.LOG_PREFIX);
    }
}
