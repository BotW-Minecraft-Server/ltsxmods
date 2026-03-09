package link.botwmcs.ltsxlogica.core;

import link.botwmcs.core.api.module.CoreModuleContext;
import link.botwmcs.core.api.module.ICoreModule;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import link.botwmcs.ltsxlogica.api.heat.IHeatService;
import link.botwmcs.ltsxlogica.heat.HeatFeature;
import link.botwmcs.ltsxlogica.heat.service.HeatServiceImpl;

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
        ctx.logger().info("{}Registered heat feature and IHeatService bridge.", LOG_PREFIX);
    }
}
