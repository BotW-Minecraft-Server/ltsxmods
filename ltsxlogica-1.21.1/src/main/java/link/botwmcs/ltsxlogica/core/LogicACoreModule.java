package link.botwmcs.ltsxlogica.core;

import link.botwmcs.core.api.command.LtsxCommandRegistrar;
import link.botwmcs.core.api.module.CoreModuleContext;
import link.botwmcs.core.api.module.ICoreModule;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import link.botwmcs.ltsxlogica.api.heat.IHeatService;
import link.botwmcs.ltsxlogica.heat.HeatFeature;
import link.botwmcs.ltsxlogica.heat.service.HeatServiceImpl;
import link.botwmcs.ltsxlogica.api.mastery.IMasteryRegistry;
import link.botwmcs.ltsxlogica.api.mastery.IMasteryService;
import link.botwmcs.ltsxlogica.api.mastery.IRuneService;
import link.botwmcs.ltsxlogica.api.mastery.IPlayerStatisticsService;
import link.botwmcs.ltsxlogica.mastery.MasteryFeature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

/**
 * ltsxcore module adapter for ltsxlogica.
 */
public final class LogicACoreModule implements ICoreModule {
    private static final int LOAD_ORDER = 200;
    private static final String LOG_PREFIX = "[ltsxlogica] ";

    @Override
    public String moduleId() {
        return LTSXLogicA.MODID;
    }

    @Override
    public int loadOrder() {
        return LOAD_ORDER;
    }

    @Override
    public void onRegister(CoreModuleContext ctx) {
        HeatFeature.init(ctx.modBus(), ctx.neoForgeBus());
        CoreServices.registerIfAbsent(IHeatService.class, new HeatServiceImpl());
        MasteryFeature.init(ctx.neoForgeBus());
        var mastery = MasteryFeature.manager();
        CoreServices.registerIfAbsent(IMasteryRegistry.class, mastery.registry());
        CoreServices.registerIfAbsent(IMasteryService.class, mastery);
        CoreServices.registerIfAbsent(IRuneService.class, mastery);
        CoreServices.registerIfAbsent(IPlayerStatisticsService.class, mastery);
        ctx.logger().info("{}Registered heat and mastery features and service bridges.", LOG_PREFIX);
    }

    @Override
    public void registerLtsxCommands(LtsxCommandRegistrar registrar) {
        registrar.menu(
                "logica",
                Component.literal("Logic A Modules"),
                Component.literal("LTSX Logic A"),
                logica -> {
                    logica.action("mastery", Component.literal("Open mastery rune application"), context -> {
                        MasteryFeature.manager().openApplicationScreen(context.getSource().getPlayerOrException());
                        return 1;
                    });
                    logica.action("rune", Component.literal("Give an Efficiency I rune (operator testing)"),
                            source -> source.hasPermission(2), context -> {
                        var player = context.getSource().getPlayerOrException();
                        var item = MasteryFeature.manager().createRune(ResourceLocation.parse("ltsxlogica:efficiency_i"));
                        if (!player.getInventory().add(item)) player.drop(item, false);
                        return 1;
                    });
                    logica.menu(
                        "heat",
                        Component.literal("Heat Module"),
                        heat -> {
                        }
                    );
                }
        );
    }
}
