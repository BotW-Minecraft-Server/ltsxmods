package link.botwmcs.ltsxlogica.api.mastery;

import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable datapack definition. Stage conditions are absolute, not per-level statistic deltas. */
public record MasteryDefinition(ResourceLocation id, String name, Set<ResourceLocation> allowedSeries,
                                 MasteryStage unlock, Map<Integer, MasteryStage> stages) {
    public MasteryDefinition {
        allowedSeries = Set.copyOf(allowedSeries);
        stages = Map.copyOf(stages);
        if (name.isBlank() || name.length() > 128) throw new IllegalArgumentException("Invalid mastery name");
        if (unlock.level() != 1) throw new IllegalArgumentException("Unlock stage must be level 1");
        int max = stages.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
        for (int level = 2; level <= max; level++) {
            MasteryStage stage = stages.get(level);
            if (stage == null || stage.level() != level || stage.conditions().isEmpty()) {
                throw new IllegalArgumentException("Stages must be contiguous from 2, with non-empty conditions");
            }
        }
        if (stages.containsKey(1)) throw new IllegalArgumentException("Use unlock for level 1");
    }
    public int advance(int currentLevel, java.util.function.ToLongFunction<StatisticSelector> statistics) {
        int level = Math.max(1, Math.min(100, currentLevel));
        while (level < 100 && stages.containsKey(level + 1) && stages.get(level + 1).matches(statistics)) level++;
        return level;
    }
}
