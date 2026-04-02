package link.botwmcs.core;

import com.mojang.logging.LogUtils;
import link.botwmcs.core.api.module.CoreModuleContext;
import link.botwmcs.core.command.CoreCommands;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.data.CoreData;
import link.botwmcs.core.event.CoreEvents;
import link.botwmcs.core.service.fizzy.FizzyBootstrap;
import link.botwmcs.core.service.tty.TtyBootstrap;
import link.botwmcs.core.module.ModuleManager;
import link.botwmcs.core.net.CoreNetwork;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.core.util.CoreIds;
import link.botwmcs.core.util.CoreKeys;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Main entrypoint for ltsxcore.
 * <p>
 * Thread/performance note: constructor only performs lightweight bootstrap and listener wiring.
 */
@Mod(CoreIds.MOD_ID)
public final class LtsxCoreMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public LtsxCoreMod(IEventBus modBus) {
        CoreServices.bootstrap();
        FizzyBootstrap.bootstrap(LOGGER, modBus);
        CoreConfig.init(modBus);
        TtyBootstrap.bootstrap(LOGGER, NeoForge.EVENT_BUS);
        CoreNetwork.register(modBus);
        CoreData.bootstrap();
        CoreEvents.init(NeoForge.EVENT_BUS);
        CoreCommands.init(NeoForge.EVENT_BUS);

        ModuleManager.discoverAndLoad(new CoreModuleContext(
                CoreIds.MOD_ID,
                LOGGER,
                NeoForge.EVENT_BUS,
                modBus
        ));
        FizzyBootstrap.applyContributors(LOGGER);

        LOGGER.info("{}Bootstrap finished.", CoreKeys.LOG_PREFIX);
    }
}
