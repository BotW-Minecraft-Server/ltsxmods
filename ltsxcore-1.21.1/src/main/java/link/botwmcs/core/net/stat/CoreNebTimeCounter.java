package link.botwmcs.core.net.stat;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.Util;

/**
 * Sliding window byte counter for speed calculation.
 */
public final class CoreNebTimeCounter {
    private final Long2IntOpenHashMap container = new Long2IntOpenHashMap();
    private final int windowSizeMs;

    public CoreNebTimeCounter(int windowSizeMs) {
        this.windowSizeMs = windowSizeMs;
    }

    public CoreNebTimeCounter() {
        this(2_000);
    }

    private void update(long now) {
        container.keySet().removeIf(then -> now - then > windowSizeMs);
    }

    public synchronized void put(int value) {
        long now = Util.getMillis();
        update(now);
        container.put(now, value);
    }

    public synchronized double averageIn1s() {
        update(Util.getMillis());
        return container.values().intStream().sum() / (double) windowSizeMs * 1_000.0d;
    }
}
