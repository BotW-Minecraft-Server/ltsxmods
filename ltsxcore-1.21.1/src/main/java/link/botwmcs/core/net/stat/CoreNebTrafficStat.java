package link.botwmcs.core.net.stat;

import java.util.concurrent.atomic.AtomicLong;
import link.botwmcs.core.config.CoreConfig;

/**
 * Core NEB transport traffic stats.
 */
public final class CoreNebTrafficStat {
    private static final AtomicLong INBOUND_BYTES_BAKED = new AtomicLong();
    private static final AtomicLong INBOUND_BYTES_RAW = new AtomicLong();
    private static final AtomicLong OUTBOUND_BYTES_BAKED = new AtomicLong();
    private static final AtomicLong OUTBOUND_BYTES_RAW = new AtomicLong();

    private static final CoreNebTimeCounter INBOUND_SPEED_BAKED = new CoreNebTimeCounter();
    private static final CoreNebTimeCounter INBOUND_SPEED_RAW = new CoreNebTimeCounter();
    private static final CoreNebTimeCounter OUTBOUND_SPEED_BAKED = new CoreNebTimeCounter();
    private static final CoreNebTimeCounter OUTBOUND_SPEED_RAW = new CoreNebTimeCounter();

    private static final AtomicLong GLOBAL_INBOUND_BYTES_BAKED = new AtomicLong();
    private static final AtomicLong GLOBAL_INBOUND_BYTES_RAW = new AtomicLong();
    private static final AtomicLong GLOBAL_OUTBOUND_BYTES_BAKED = new AtomicLong();
    private static final AtomicLong GLOBAL_OUTBOUND_BYTES_RAW = new AtomicLong();

    private static final CoreNebTimeCounter GLOBAL_INBOUND_SPEED_BAKED = new CoreNebTimeCounter();
    private static final CoreNebTimeCounter GLOBAL_INBOUND_SPEED_RAW = new CoreNebTimeCounter();
    private static final CoreNebTimeCounter GLOBAL_OUTBOUND_SPEED_BAKED = new CoreNebTimeCounter();
    private static final CoreNebTimeCounter GLOBAL_OUTBOUND_SPEED_RAW = new CoreNebTimeCounter();

    private CoreNebTrafficStat() {
    }

    public static void inBaked(int size) {
        if (size <= 0) {
            return;
        }
        INBOUND_BYTES_BAKED.addAndGet(size);
        INBOUND_SPEED_BAKED.put(size);
    }

    public static void inRaw(int size) {
        if (size <= 0) {
            return;
        }
        INBOUND_BYTES_RAW.addAndGet(size);
        INBOUND_SPEED_RAW.put(size);
    }

    public static void outBaked(int size) {
        if (size <= 0) {
            return;
        }
        OUTBOUND_BYTES_BAKED.addAndGet(size);
        OUTBOUND_SPEED_BAKED.put(size);
    }

    public static void outRaw(int size) {
        if (size <= 0) {
            return;
        }
        OUTBOUND_BYTES_RAW.addAndGet(size);
        OUTBOUND_SPEED_RAW.put(size);
    }

    public static void globalInBaked(int size) {
        if (size <= 0) {
            return;
        }
        GLOBAL_INBOUND_BYTES_BAKED.addAndGet(size);
        GLOBAL_INBOUND_SPEED_BAKED.put(size);
    }

    public static void globalInRaw(int size) {
        if (size <= 0) {
            return;
        }
        GLOBAL_INBOUND_BYTES_RAW.addAndGet(size);
        GLOBAL_INBOUND_SPEED_RAW.put(size);
    }

    public static void globalOutBaked(int size) {
        if (size <= 0) {
            return;
        }
        GLOBAL_OUTBOUND_BYTES_BAKED.addAndGet(size);
        GLOBAL_OUTBOUND_SPEED_BAKED.put(size);
    }

    public static void globalOutRaw(int size) {
        if (size <= 0) {
            return;
        }
        GLOBAL_OUTBOUND_BYTES_RAW.addAndGet(size);
        GLOBAL_OUTBOUND_SPEED_RAW.put(size);
    }

    public static Snapshot snapshotCore() {
        return new Snapshot(
                INBOUND_BYTES_BAKED.get(),
                INBOUND_BYTES_RAW.get(),
                OUTBOUND_BYTES_BAKED.get(),
                OUTBOUND_BYTES_RAW.get(),
                INBOUND_SPEED_BAKED.averageIn1s(),
                INBOUND_SPEED_RAW.averageIn1s(),
                OUTBOUND_SPEED_BAKED.averageIn1s(),
                OUTBOUND_SPEED_RAW.averageIn1s()
        );
    }

    public static Snapshot snapshotGlobal() {
        return new Snapshot(
                GLOBAL_INBOUND_BYTES_BAKED.get(),
                GLOBAL_INBOUND_BYTES_RAW.get(),
                GLOBAL_OUTBOUND_BYTES_BAKED.get(),
                GLOBAL_OUTBOUND_BYTES_RAW.get(),
                GLOBAL_INBOUND_SPEED_BAKED.averageIn1s(),
                GLOBAL_INBOUND_SPEED_RAW.averageIn1s(),
                GLOBAL_OUTBOUND_SPEED_BAKED.averageIn1s(),
                GLOBAL_OUTBOUND_SPEED_RAW.averageIn1s()
        );
    }

    public static Snapshot snapshot() {
        return CoreConfig.nebGlobalMixinEnabled() ? snapshotGlobal() : snapshotCore();
    }

    public record Snapshot(
            long inboundBytesBaked,
            long inboundBytesRaw,
            long outboundBytesBaked,
            long outboundBytesRaw,
            double inboundSpeedBaked,
            double inboundSpeedRaw,
            double outboundSpeedBaked,
            double outboundSpeedRaw
    ) {
    }
}
