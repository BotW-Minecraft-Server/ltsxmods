package link.botwmcs.ltsxlogica.mastery.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import link.botwmcs.ltsxlogica.api.mastery.IPlayerStatisticProvider;
import link.botwmcs.ltsxlogica.api.mastery.StatisticSelector;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.tags.TagKey;

public final class VanillaStatisticProvider implements IPlayerStatisticProvider {
    private final Map<StatisticSelector, List<Stat<?>>> cache = new HashMap<>();
    public ResourceLocation id() { return StatisticSelector.VANILLA; }
    public void clearCache() { cache.clear(); }
    public void validate(StatisticSelector selector) {
        StatType<?> type = BuiltInRegistries.STAT_TYPE.getOptional(selector.statType())
                .orElseThrow(() -> new IllegalArgumentException("Unknown stat type: " + selector.statType()));
        for (ResourceLocation id : selector.ids()) if (!type.getRegistry().containsKey(id)) {
            throw new IllegalArgumentException("Unknown statistic value: " + selector.statType() + "/" + id);
        }
    }
    public long query(ServerPlayer player, StatisticSelector selector) {
        List<Stat<?>> stats = cache.computeIfAbsent(selector, this::resolve);
        long value = 0;
        for (Stat<?> stat : stats) value = Math.addExact(value, Math.max(0, player.getStats().getValue(stat)));
        return value;
    }
    private List<Stat<?>> resolve(StatisticSelector selector) {
        validate(selector);
        return resolve(BuiltInRegistries.STAT_TYPE.get(selector.statType()), selector);
    }
    private static <T> List<Stat<?>> resolve(StatType<T> type, StatisticSelector selector) {
        Registry<T> registry = type.getRegistry();
        List<Stat<?>> stats = new ArrayList<>();
        if (selector.all()) for (T value : registry) stats.add(type.get(value));
        else if (selector.tag().isPresent()) registry.getTag(TagKey.create(registry.key(), selector.tag().get()))
                .ifPresent(values -> values.forEach(holder -> stats.add(type.get(holder.value()))));
        else for (ResourceLocation id : selector.ids()) stats.add(type.get(registry.get(id)));
        return List.copyOf(stats);
    }
}
