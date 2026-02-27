package link.botwmcs.ltsxlogica.heat.client;

import link.botwmcs.ltsxlogica.heat.BiomeAmbientModel;
import link.botwmcs.ltsxlogica.heat.HeatManager;
import link.botwmcs.ltsxlogica.heat.HeatProps;
import link.botwmcs.ltsxlogica.heat.HeatPropsResolver;
import link.botwmcs.ltsxlogica.heat.network.HeatClientSyncState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws player thermal info at top-center HUD.
 *
 * Priority:
 * 1) use server-synced "entity received temperature" when available
 * 2) fallback: estimate from block under player's feet + ambient model
 */
final class HeatHudOverlay {
    private static final int FALLBACK_UPDATE_PERIOD_TICKS = 5;
    private static final int SERVER_SYNC_STALE_TICKS = 40;
    private static final int TEXT_COLOR_SYNCED = 0xFFFFD966;
    private static final int TEXT_COLOR_FALLBACK = 0xFFB0BEC5;

    private final HeatPropsResolver propsResolver = new HeatPropsResolver();
    private final BlockPos.MutableBlockPos probePos = new BlockPos.MutableBlockPos();

    private int displayTempFixed = HeatManager.toFixed(20.0f);
    private int lastFallbackUpdateClientTick = Integer.MIN_VALUE;

    void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (HeatClientSyncState.hasFreshSync(SERVER_SYNC_STALE_TICKS)) {
            this.displayTempFixed = HeatClientSyncState.getSyncedTempFixed();
            return;
        }

        int clientTick = mc.player.tickCount;
        if (clientTick - lastFallbackUpdateClientTick < FALLBACK_UPDATE_PERIOD_TICKS) {
            return;
        }
        lastFallbackUpdateClientTick = clientTick;

        this.displayTempFixed = estimateFootTemperatureFixed(mc.level, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
    }

    void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        boolean fromServerSync = HeatClientSyncState.hasFreshSync(SERVER_SYNC_STALE_TICKS);
        float tempC = HeatManager.toCelsius(this.displayTempFixed);
        String label = fromServerSync
                ? String.format("Heat %.1f C", tempC)
                : String.format("Heat~ %.1f C (foot)", tempC);

        int width = mc.getWindow().getGuiScaledWidth();
        int x = (width - mc.font.width(label)) / 2;
        int y = 6; // HUD top area
        int color = fromServerSync ? TEXT_COLOR_SYNCED : TEXT_COLOR_FALLBACK;
        guiGraphics.drawString(mc.font, label, x, y, color, true);
    }

    private int estimateFootTemperatureFixed(Level level, int x, int y, int z) {
        this.probePos.set(x, y - 1, z);
        BlockState below = level.getBlockState(this.probePos);
        HeatProps props = this.propsResolver.resolve(below);

        int ambient = ambientFixed(level, x, y, z);
        if (props.hasTarget()) {
            return props.targetFixed;
        }

        // Simple fallback estimation:
        // ambient + small adjustment by local generation/relax properties.
        int estimate = ambient + Math.round(props.generationQ * 0.5f) - Math.round(props.relaxR * 16.0f);
        return Mth.clamp(estimate, HeatManager.MIN_TEMP_FIXED, HeatManager.MAX_TEMP_FIXED);
    }

    private int ambientFixed(Level level, int x, int y, int z) {
        int sampleY = Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        int biomeBase = blendedBiomeBaseFixed(level, x, sampleY, z);
        int gradient = (level.getSeaLevel() - y) >> 3;
        int ambient = biomeBase + gradient;
        return Mth.clamp(ambient, HeatManager.MIN_TEMP_FIXED, HeatManager.MAX_TEMP_FIXED);
    }

    private int blendedBiomeBaseFixed(Level level, int x, int y, int z) {
        int shiftedX = x - 2;
        int shiftedY = y - 2;
        int shiftedZ = z - 2;

        int qx0 = Math.floorDiv(shiftedX, 4);
        int qy0 = Math.floorDiv(shiftedY, 4);
        int qz0 = Math.floorDiv(shiftedZ, 4);
        int qx1 = qx0 + 1;
        int qy1 = qy0 + 1;
        int qz1 = qz0 + 1;

        int fx = Math.floorMod(shiftedX, 4);
        int fy = Math.floorMod(shiftedY, 4);
        int fz = Math.floorMod(shiftedZ, 4);
        int wx0 = 4 - fx;
        int wy0 = 4 - fy;
        int wz0 = 4 - fz;
        int wx1 = fx;
        int wy1 = fy;
        int wz1 = fz;

        long sum = 0L;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy0, qz0) * wx0 * wy0 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy0, qz0) * wx1 * wy0 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy1, qz0) * wx0 * wy1 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy1, qz0) * wx1 * wy1 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy0, qz1) * wx0 * wy0 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy0, qz1) * wx1 * wy0 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy1, qz1) * wx0 * wy1 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy1, qz1) * wx1 * wy1 * wz1;
        return (int) Math.round(sum / 64.0d);
    }

    private int sampleBiomeBaseFixedAtQuart(Level level, int quartX, int quartY, int quartZ) {
        int sampleX = QuartPos.toBlock(quartX) + 2;
        int sampleY = QuartPos.toBlock(quartY) + 2;
        int sampleZ = QuartPos.toBlock(quartZ) + 2;
        int clampedY = Mth.clamp(sampleY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        this.probePos.set(sampleX, clampedY, sampleZ);
        return BiomeAmbientModel.resolveAmbientFixed(level, level.getBiome(this.probePos));
    }
}
