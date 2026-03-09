package link.botwmcs.ltsxlogica.api.heat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * External heat API exposed via CoreServices for cross-module integrations.
 */
public interface IHeatService {
    int getTemperatureFixed(ServerLevel level, int x, int y, int z);

    float getTemperatureCelsius(ServerLevel level, BlockPos pos);

    void setTemperatureFixed(ServerLevel level, int x, int y, int z, int fixedTemp);

    void markBlockAndNeighborsDirty(ServerLevel level, int x, int y, int z);
}
