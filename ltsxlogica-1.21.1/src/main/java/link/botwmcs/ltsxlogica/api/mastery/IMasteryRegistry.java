package link.botwmcs.ltsxlogica.api.mastery;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Server definitions; datapack reload replaces both maps atomically. */
public interface IMasteryRegistry {
    long revision();
    Map<ResourceLocation, MasteryDefinition> masteries();
    Map<ResourceLocation, RuneDefinition> runes();
    default Optional<MasteryDefinition> mastery(ResourceLocation id) { return Optional.ofNullable(masteries().get(id)); }
    default Optional<RuneDefinition> rune(ResourceLocation id) { return Optional.ofNullable(runes().get(id)); }
}
