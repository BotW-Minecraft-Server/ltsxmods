package link.botwmcs.ltsxlogica.api.mastery;

import java.util.List;
import java.util.function.ToLongFunction;

/** A stage is checked against committed cumulative statistics; any=false means AND. */
public record MasteryStage(int level, boolean any, List<StatisticCondition> conditions) {
    public MasteryStage {
        if (level < 1 || level > 100) throw new IllegalArgumentException("Level must be 1..100");
        conditions = List.copyOf(conditions);
        if (conditions.size() > 32) throw new IllegalArgumentException("Too many conditions");
    }
    public boolean matches(ToLongFunction<StatisticSelector> statistics) {
        if (conditions.isEmpty()) return true;
        return any ? conditions.stream().anyMatch(c -> c.test(statistics, level))
                : conditions.stream().allMatch(c -> c.test(statistics, level));
    }
}
