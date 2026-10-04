package link.botwmcs.ltsxlogica.api.mastery;

import java.math.BigDecimal;
import java.util.Objects;

/** Constant, or base + perLevel * (targetLevel + levelOffset)^power. No executable expressions. */
public record NumericThreshold(BigDecimal base, BigDecimal perLevel, int levelOffset, int power) {
    public NumericThreshold {
        Objects.requireNonNull(base);
        Objects.requireNonNull(perLevel);
        if (power < 0 || power > 4 || Math.abs((long) levelOffset) > 100) {
            throw new IllegalArgumentException("Threshold power must be 0..4 and offset -100..100");
        }
        if (base.precision() > 32 || perLevel.precision() > 32 || Math.abs((long) base.scale()) > 32
                || Math.abs((long) perLevel.scale()) > 32) throw new IllegalArgumentException("Threshold too large");
    }

    public static NumericThreshold constant(BigDecimal value) {
        return new NumericThreshold(value, BigDecimal.ZERO, 0, 1);
    }

    public BigDecimal atLevel(int targetLevel) {
        return base.add(perLevel.multiply(BigDecimal.valueOf((long) targetLevel + levelOffset).pow(power)));
    }
}
