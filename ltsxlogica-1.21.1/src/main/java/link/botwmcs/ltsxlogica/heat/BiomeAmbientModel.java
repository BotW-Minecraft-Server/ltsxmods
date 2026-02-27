package link.botwmcs.ltsxlogica.heat;

import link.botwmcs.ltsxlogica.data.HeatDataRegistry;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Biome-driven ambient baseline model.
 *
 * Values are in celsius and converted to fixed-point for runtime heat simulation.
 */
public final class BiomeAmbientModel {
    private BiomeAmbientModel() {
    }

    public static int resolveAmbientFixed(Holder<Biome> biomeHolder) {
        return HeatDataRegistry.model().biomeModel().resolveAmbientFixed(biomeHolder);
    }

    public static int resolveAmbientFixed(Level level, Holder<Biome> biomeHolder) {
        int baseAmbient = resolveAmbientFixed(biomeHolder);
        int seasonalOffset = EclipticSeasonBridge.resolveSeasonalOffsetFixed(level);
        if (seasonalOffset == 0) {
            return baseAmbient;
        }
        return Mth.clamp(
                baseAmbient + seasonalOffset,
                HeatManager.MIN_TEMP_FIXED,
                HeatManager.MAX_TEMP_FIXED
        );
    }
}
