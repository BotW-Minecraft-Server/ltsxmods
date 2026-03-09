package link.botwmcs.ltsxlogica.heat.service;

import link.botwmcs.ltsxlogica.api.heat.IHeatService;
import link.botwmcs.ltsxlogica.heat.HeatFeature;
import link.botwmcs.ltsxlogica.heat.HeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Default IHeatService implementation backed by HeatFeature/HeatManager.
 */
public final class HeatServiceImpl implements IHeatService {
    @Override
    public int getTemperatureFixed(ServerLevel level, int x, int y, int z) {
        return manager(level).getTemperatureFixed(level, x, y, z);
    }

    @Override
    public float getTemperatureCelsius(ServerLevel level, BlockPos pos) {
        return manager(level).getTemperature(level, pos);
    }

    @Override
    public void setTemperatureFixed(ServerLevel level, int x, int y, int z, int fixedTemp) {
        manager(level).setTemperatureFixed(level, x, y, z, fixedTemp);
    }

    @Override
    public void markBlockAndNeighborsDirty(ServerLevel level, int x, int y, int z) {
        manager(level).markBlockAndNeighborsDirty(level, x, y, z);
    }

    private static HeatManager manager(ServerLevel level) {
        return HeatFeature.getManager(level);
    }
}
