package link.botwmcs.core.net.stat;

import java.util.concurrent.atomic.AtomicLong;

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

    public static Snapshot snapshot() {
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
