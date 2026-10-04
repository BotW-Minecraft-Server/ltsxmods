package link.botwmcs.ltsxlogica.api.mastery;

import java.util.Objects;
import java.util.function.ToLongFunction;

public record StatisticCondition(StatisticSelector statistic, ComparisonOperator operator, NumericThreshold value) {
    public StatisticCondition { Objects.requireNonNull(statistic); Objects.requireNonNull(operator); Objects.requireNonNull(value); }
    public boolean test(ToLongFunction<StatisticSelector> statistics, int targetLevel) {
        return operator.test(statistics.applyAsLong(statistic), value.atLevel(targetLevel));
    }
}
