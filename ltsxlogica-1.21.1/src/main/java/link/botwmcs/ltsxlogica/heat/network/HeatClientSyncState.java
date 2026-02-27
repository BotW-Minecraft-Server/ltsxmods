package link.botwmcs.ltsxlogica.heat.network;

import link.botwmcs.ltsxlogica.heat.HeatManager;
import net.minecraft.util.Mth;

/**
 * Client-side cache written by network handlers and read by HUD rendering code.
 *
 * Kept in common code (no client-only imports) so payload handlers can stay side-safe.
 */
public final class HeatClientSyncState {
    private static volatile int syncedTempFixed = HeatManager.toFixed(20.0f);
    private static volatile int ageTicks = Integer.MAX_VALUE;

    private HeatClientSyncState() {
    }

    public static void pushSyncedTemperature(int tempFixed) {
        syncedTempFixed = Mth.clamp(tempFixed, HeatManager.MIN_TEMP_FIXED, HeatManager.MAX_TEMP_FIXED);
        ageTicks = 0;
    }

    public static void onClientTick() {
        if (ageTicks < Integer.MAX_VALUE) {
            ageTicks++;
        }
    }

    public static boolean hasFreshSync(int maxAgeTicks) {
        return ageTicks <= maxAgeTicks;
    }

    public static int getSyncedTempFixed() {
        return syncedTempFixed;
    }
}

