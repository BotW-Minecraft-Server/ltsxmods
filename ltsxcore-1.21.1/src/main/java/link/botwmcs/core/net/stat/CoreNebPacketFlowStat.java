package link.botwmcs.core.net.stat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-packet-type NEB traffic table (inbound/outbound).
 */
public final class CoreNebPacketFlowStat {
    private static final Map<Key, PacketCounter> INBOUND = new ConcurrentHashMap<>();
    private static final Map<Key, PacketCounter> OUTBOUND = new ConcurrentHashMap<>();

    private CoreNebPacketFlowStat() {
    }

    public static void in(ResourceLocation type, int size) {
        in(type, size, TrafficPath.NEB);
    }

    public static void in(ResourceLocation type, int size, TrafficPath path) {
        record(INBOUND, type, size, path);
    }

    public static void out(ResourceLocation type, int size) {
        out(type, size, TrafficPath.NEB);
    }

    public static void out(ResourceLocation type, int size, TrafficPath path) {
        record(OUTBOUND, type, size, path);
    }

    public static Snapshot snapshot(int limit) {
        int topN = Math.max(1, limit);
        return new Snapshot(top(INBOUND, topN), top(OUTBOUND, topN));
    }

    private static void record(Map<Key, PacketCounter> map, ResourceLocation type, int size, TrafficPath path) {
        if (type == null || size <= 0) {
            return;
        }
        map.computeIfAbsent(new Key(type, path), ignored -> new PacketCounter()).put(size);
    }

    private static List<Row> top(Map<Key, PacketCounter> map, int limit) {
        return map.entrySet().stream()
                .map(entry -> {
                    PacketCounter counter = entry.getValue();
                    return new Row(
                            entry.getKey().type(),
                            entry.getKey().path(),
                            counter.totalBytes.get(),
                            counter.totalPackets.get(),
                            counter.speedBytes.averageIn1s()
                    );
                })
                .sorted(Comparator.comparingDouble(Row::speedBytesPerSecond).reversed()
                        .thenComparingLong(Row::totalBytes).reversed()
                        .thenComparing(Row::path)
                        .thenComparing(row -> row.type().toString()))
                .limit(limit)
                .toList();
    }

    public record Row(
            ResourceLocation type,
            TrafficPath path,
            long totalBytes,
            long totalPackets,
            double speedBytesPerSecond
    ) {
    }

    public record Snapshot(
            List<Row> inbound,
            List<Row> outbound
    ) {
    }

    private record Key(ResourceLocation type, TrafficPath path) {
    }

    public enum TrafficPath {
        NEB,
        BYPASS
    }

    private static final class PacketCounter {
        private final AtomicLong totalBytes = new AtomicLong();
        private final AtomicLong totalPackets = new AtomicLong();
        private final CoreNebTimeCounter speedBytes = new CoreNebTimeCounter();

        private void put(int size) {
            totalBytes.addAndGet(size);
            totalPackets.incrementAndGet();
            speedBytes.put(size);
        }
    }
}
