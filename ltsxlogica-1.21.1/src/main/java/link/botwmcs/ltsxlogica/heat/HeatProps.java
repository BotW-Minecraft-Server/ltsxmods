package link.botwmcs.ltsxlogica.heat;

/**
 * Thermal properties per BlockState.
 * Values are intentionally lightweight primitives for hot-loop usage.
 */
public final class HeatProps {
    public static final int NO_TARGET_FIXED = Integer.MIN_VALUE;

    public static final HeatProps AIR = new HeatProps(
            0.03f, // K
            1.0f,  // C
            0.0f,  // q
            0.015f, // r
            NO_TARGET_FIXED,
            0.0f,  // s
            0.06f, // h
            1,
            true
    );

    public final float conductivityK;
    public final float capacityC;
    public final float invCapacity;
    public final float generationQ;
    public final float relaxR;
    public final int targetFixed;
    public final float sourceStrengthS;
    public final float convectiveH;
    public final int updatePeriodTicks;
    public final boolean airLike;

    private HeatProps(
            float conductivityK,
            float capacityC,
            float generationQ,
            float relaxR,
            int targetFixed,
            float sourceStrengthS,
            float convectiveH,
            int updatePeriodTicks,
            boolean airLike
    ) {
        this.conductivityK = Math.max(0.0f, conductivityK);
        this.capacityC = Math.max(0.05f, capacityC);
        this.invCapacity = 1.0f / this.capacityC;
        this.generationQ = generationQ;
        this.relaxR = Math.max(0.0f, relaxR);
        this.targetFixed = targetFixed;
        this.sourceStrengthS = Math.max(0.0f, sourceStrengthS);
        this.convectiveH = Math.max(0.0f, convectiveH);
        this.updatePeriodTicks = Math.max(1, updatePeriodTicks);
        this.airLike = airLike;
    }

    public static HeatProps of(
            float conductivityK,
            float capacityC,
            float generationQ,
            float relaxR,
            float targetCelsius,
            float sourceStrengthS,
            float convectiveH,
            int updatePeriodTicks,
            boolean airLike
    ) {
        final int targetFixed = Float.isFinite(targetCelsius)
                ? HeatManager.toFixed(targetCelsius)
                : NO_TARGET_FIXED;
        return new HeatProps(
                conductivityK,
                capacityC,
                generationQ,
                relaxR,
                targetFixed,
                sourceStrengthS,
                convectiveH,
                updatePeriodTicks,
                airLike
        );
    }

    public static HeatProps ofNoTarget(
            float conductivityK,
            float capacityC,
            float generationQ,
            float relaxR,
            float convectiveH,
            int updatePeriodTicks,
            boolean airLike
    ) {
        return new HeatProps(
                conductivityK,
                capacityC,
                generationQ,
                relaxR,
                NO_TARGET_FIXED,
                0.0f,
                convectiveH,
                updatePeriodTicks,
                airLike
        );
    }

    public boolean hasTarget() {
        return this.targetFixed != NO_TARGET_FIXED && this.sourceStrengthS > 0.0f;
    }

    public boolean isPersistentSource() {
        return this.generationQ != 0.0f || hasTarget();
    }
}

