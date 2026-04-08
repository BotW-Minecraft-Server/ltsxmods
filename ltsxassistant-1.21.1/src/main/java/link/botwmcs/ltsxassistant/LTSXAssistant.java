package link.botwmcs.ltsxassistant;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(LTSXAssistant.MODID)
public final class LTSXAssistant {
    public static final String MODID = "ltsxassistant";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LTSXAssistant(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        if (FMLEnvironment.dist.isClient()) {
            LTSXAssistantClient.init(modEventBus);
        }
        LOGGER.info("Initialized mod {}", MODID);
    }
}
