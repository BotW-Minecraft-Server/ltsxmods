package link.botwmcs.modid;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ModMain.MODID)
public final class ModMain {
    public static final String MODID = "__MODID__";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModMain(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ModConfigSpecHolder.SPEC);
        LOGGER.info("Initialized mod {}", MODID);
    }
}
