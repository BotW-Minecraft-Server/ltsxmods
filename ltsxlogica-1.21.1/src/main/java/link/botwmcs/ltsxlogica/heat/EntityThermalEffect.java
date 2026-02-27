package link.botwmcs.ltsxlogica.heat;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.UUID;
import link.botwmcs.ltsxlogica.heat.network.HeatNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;

/**
 * Periodic entity temperature sampling hook.
 * Keep sampling interval >1 tick to avoid O(entity_count) work each tick.
 */
public final class EntityThermalEffect {
    private static final int EFFECT_SAMPLE_PERIOD_TICKS = 10;
    private static final int SYNC_SAMPLE_PERIOD_TICKS = 5;
    private static final int DAMAGE_PERIOD_TICKS = 20;
    private static final int SYNC_DELTA_FIXED = Math.max(2, HeatManager.EPSILON_FIXED);
    private static final double FACE_SAMPLE_EPSILON = 0.03125d;
    private static final int HOT_WARNING_FIXED = HeatManager.toFixed(45.0f);
    private static final int HOT_DAMAGE_FIXED = HeatManager.toFixed(80.0f);
    private static final int COLD_WARNING_FIXED = HeatManager.toFixed(0.0f);
    private static final int COLD_DAMAGE_FIXED = HeatManager.toFixed(-15.0f);

    private final Object2IntOpenHashMap<UUID> lastSyncedTempByPlayer = new Object2IntOpenHashMap<>();

    public EntityThermalEffect() {
        this.lastSyncedTempByPlayer.defaultReturnValue(Integer.MIN_VALUE);
    }

    public void tick(ServerLevel level, HeatManager heatManager) {
        long gameTime = level.getGameTime();
        boolean doEffects = (gameTime % EFFECT_SAMPLE_PERIOD_TICKS) == 0L;
        boolean doSync = (gameTime % SYNC_SAMPLE_PERIOD_TICKS) == 0L;
        if (!doEffects && !doSync) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            int tFixed = samplePerceivedTemperatureFixed(player, level, heatManager);

            if (doSync) {
                syncPlayerTemperature(player, tFixed);
            }

            if (doEffects) {
                applyThermalEffects(player, tFixed, gameTime);
            }
        }
    }

    public void onPlayerLoggedOut(ServerPlayer player) {
        this.lastSyncedTempByPlayer.removeInt(player.getUUID());
    }

    private void syncPlayerTemperature(ServerPlayer player, int tFixed) {
        UUID key = player.getUUID();
        int last = this.lastSyncedTempByPlayer.getInt(key);
        if (last != Integer.MIN_VALUE && Math.abs(tFixed - last) <= SYNC_DELTA_FIXED) {
            return;
        }

        HeatNetworking.sendEntityTemperature(player, tFixed);
        this.lastSyncedTempByPlayer.put(key, tFixed);
    }

    private static void applyThermalEffects(ServerPlayer player, int tFixed, long gameTime) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        if (tFixed >= HOT_WARNING_FIXED) {
            int burnTicks = tFixed >= HOT_DAMAGE_FIXED ? 80 : 30;
            player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), burnTicks));
            if (tFixed >= HOT_DAMAGE_FIXED && (gameTime % DAMAGE_PERIOD_TICKS) == 0L) {
                player.hurt(player.damageSources().hotFloor(), 1.0f);
            }
            // hot states dominate this tick; do not stack cold effects.
            return;
        }

        if (tFixed <= COLD_WARNING_FIXED) {
            int amp = tFixed <= COLD_DAMAGE_FIXED ? 1 : 0;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, amp, false, true, true));

            if (tFixed <= COLD_DAMAGE_FIXED) {
                int frozenNow = player.getTicksFrozen();
                int frozenAdd = 6 + Math.max(0, (COLD_DAMAGE_FIXED - tFixed) / (HeatManager.FIXED_SCALE * 5));
                player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze(), frozenNow + frozenAdd));
                if ((gameTime % DAMAGE_PERIOD_TICKS) == 0L) {
                    player.hurt(player.damageSources().freeze(), 1.0f);
                }
            }
            return;
        }

        // Recover when returning to mild temperature.
        if (player.getTicksFrozen() > 0) {
            player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 8));
        }
    }

    private static int samplePerceivedTemperatureFixed(ServerPlayer player, ServerLevel level, HeatManager heatManager) {
        AABB box = player.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5d;
        double cy = (box.minY + box.maxY) * 0.5d;
        double cz = (box.minZ + box.maxZ) * 0.5d;

        int left = samplePointTemperatureFixed(heatManager, level, box.minX - FACE_SAMPLE_EPSILON, cy, cz);
        int right = samplePointTemperatureFixed(heatManager, level, box.maxX + FACE_SAMPLE_EPSILON, cy, cz);
        int down = samplePointTemperatureFixed(heatManager, level, cx, box.minY - FACE_SAMPLE_EPSILON, cz);
        int up = samplePointTemperatureFixed(heatManager, level, cx, box.maxY + FACE_SAMPLE_EPSILON, cz);
        int north = samplePointTemperatureFixed(heatManager, level, cx, cy, box.minZ - FACE_SAMPLE_EPSILON);
        int south = samplePointTemperatureFixed(heatManager, level, cx, cy, box.maxZ + FACE_SAMPLE_EPSILON);

        int sum = left + right + down + up + north + south;
        return Math.round(sum / 6.0f);
    }

    private static int samplePointTemperatureFixed(HeatManager heatManager, ServerLevel level, double x, double y, double z) {
        return heatManager.getTemperatureFixed(level, Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }
}
