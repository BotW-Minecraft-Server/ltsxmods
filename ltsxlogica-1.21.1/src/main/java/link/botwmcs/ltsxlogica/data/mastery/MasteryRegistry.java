package link.botwmcs.ltsxlogica.data.mastery;

import java.util.Map;
import link.botwmcs.ltsxlogica.api.mastery.IMasteryRegistry;
import link.botwmcs.ltsxlogica.api.mastery.MasteryDefinition;
import link.botwmcs.ltsxlogica.api.mastery.RuneDefinition;
import net.minecraft.resources.ResourceLocation;

public final class MasteryRegistry implements IMasteryRegistry {
    private record Version(long revision, MasteryDefinitions definitions) {}
    private volatile Version current = new Version(0, new MasteryDefinitions(Map.of(), Map.of()));
    public long revision() { return current.revision(); }
    public Map<ResourceLocation, MasteryDefinition> masteries() { return current.definitions().masteries(); }
    public Map<ResourceLocation, RuneDefinition> runes() { return current.definitions().runes(); }
    public synchronized void publish(MasteryDefinitions definitions) { current = new Version(current.revision() + 1, definitions); }
}
