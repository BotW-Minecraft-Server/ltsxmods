package link.botwmcs.ltsxlogica.data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global hot-reloadable heat model snapshot.
 */
public final class HeatDataRegistry {
    private static final AtomicInteger REVISION = new AtomicInteger(1);
    private static volatile HeatModelData MODEL = HeatModelData.defaults();

    private HeatDataRegistry() {
    }

    public static HeatModelData model() {
        return MODEL;
    }

    public static int revision() {
        return REVISION.get();
    }

    public static void setModel(HeatModelData model) {
        MODEL = model;
        REVISION.incrementAndGet();
    }
}
