package link.botwmcs.ltsxlogica.api.mastery;

import java.math.BigDecimal;

/** Exact numeric comparisons used by datapack stage conditions. */
public enum ComparisonOperator {
    GREATER_THAN(">"), LESS_THAN("<"), GREATER_OR_EQUAL(">="), LESS_OR_EQUAL("<="), EQUAL("==");

    private final String symbol;

    ComparisonOperator(String symbol) { this.symbol = symbol; }
    public String symbol() { return symbol; }

    public boolean test(long actual, BigDecimal expected) {
        int comparison = BigDecimal.valueOf(actual).compareTo(expected);
        return switch (this) {
            case GREATER_THAN -> comparison > 0;
            case LESS_THAN -> comparison < 0;
            case GREATER_OR_EQUAL -> comparison >= 0;
            case LESS_OR_EQUAL -> comparison <= 0;
            case EQUAL -> comparison == 0;
        };
    }

    public static ComparisonOperator parse(String value) {
        if (value.equals("=")) return EQUAL;
        for (ComparisonOperator operator : values()) if (operator.symbol.equals(value)) return operator;
        throw new IllegalArgumentException("Unsupported comparison: " + value);
    }
}
