package link.botwmcs.ltsxlogica.data.mastery;

import java.util.Map;
import link.botwmcs.ltsxlogica.api.mastery.MasteryDefinition;
import link.botwmcs.ltsxlogica.api.mastery.RuneDefinition;
import net.minecraft.resources.ResourceLocation;

public record MasteryDefinitions(Map<ResourceLocation, MasteryDefinition> masteries,
                                Map<ResourceLocation, RuneDefinition> runes) {
    public MasteryDefinitions {
        masteries = Map.copyOf(masteries);
        runes = Map.copyOf(runes);
        if (masteries.size() > 64 || runes.size() > 256) throw new IllegalArgumentException("Too many mastery/rune definitions (64/256)");
    }
}
