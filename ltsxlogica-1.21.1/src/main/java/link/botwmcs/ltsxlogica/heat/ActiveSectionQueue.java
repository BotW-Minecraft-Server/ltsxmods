package link.botwmcs.ltsxlogica.heat;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * FIFO queue for active sections with de-duplication and sleep/wake scheduling.
 */
public final class ActiveSectionQueue {
    public static final long NO_SECTION = Long.MIN_VALUE;

    private final LongArrayFIFOQueue fifo = new LongArrayFIFOQueue();
    private final LongOpenHashSet inQueue = new LongOpenHashSet();
    private final Long2LongOpenHashMap sleepingUntilTick = new Long2LongOpenHashMap();

    public ActiveSectionQueue() {
        this.sleepingUntilTick.defaultReturnValue(Long.MIN_VALUE);
    }

    public boolean enqueue(long sectionKey, long nowTick) {
        long wakeTick = this.sleepingUntilTick.get(sectionKey);
        if (wakeTick != Long.MIN_VALUE && nowTick < wakeTick) {
            return false;
        }
        if (wakeTick != Long.MIN_VALUE) {
            this.sleepingUntilTick.remove(sectionKey);
        }
        if (this.inQueue.add(sectionKey)) {
            this.fifo.enqueue(sectionKey);
            return true;
        }
        return false;
    }

    public void requeue(long sectionKey, long nowTick) {
        enqueue(sectionKey, nowTick);
    }

    public long poll(long nowTick) {
        while (!this.fifo.isEmpty()) {
            long key = this.fifo.dequeueLong();
            this.inQueue.remove(key);

            long wakeTick = this.sleepingUntilTick.get(key);
            if (wakeTick != Long.MIN_VALUE && nowTick < wakeTick) {
                continue;
            }
            if (wakeTick != Long.MIN_VALUE) {
                this.sleepingUntilTick.remove(key);
            }
            return key;
        }
        return NO_SECTION;
    }

    public void sleep(long sectionKey, long nowTick, int sleepTicks) {
        this.inQueue.remove(sectionKey);
        this.sleepingUntilTick.put(sectionKey, nowTick + Math.max(1, sleepTicks));
    }

    public void evict(long sectionKey) {
        this.inQueue.remove(sectionKey);
        this.sleepingUntilTick.remove(sectionKey);
    }

    /**
     * Wake up to maxWakeups expired sleeping sections each tick.
     */
    public int wakeExpired(long nowTick, int maxWakeups) {
        int woke = 0;
        Long2LongMap.FastEntrySet entries = this.sleepingUntilTick.long2LongEntrySet();
        var iterator = entries.fastIterator();
        while (iterator.hasNext() && woke < maxWakeups) {
            Long2LongMap.Entry entry = iterator.next();
            if (entry.getLongValue() <= nowTick) {
                long sectionKey = entry.getLongKey();
                iterator.remove();
                if (this.inQueue.add(sectionKey)) {
                    this.fifo.enqueue(sectionKey);
                    woke++;
                }
            }
        }
        return woke;
    }
}

