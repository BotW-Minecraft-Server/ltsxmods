package link.botwmcs.ltsxlogica.data.mastery;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Map;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import link.botwmcs.ltsxlogica.mastery.MasteryManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class MasteryReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "ltsxlogica/mastery";
    private final MasteryManager manager;
    public MasteryReloadListener(MasteryManager manager) { super(new Gson(), DIRECTORY); this.manager = manager; }
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resources, ProfilerFiller profiler) {
        try {
            MasteryDefinitions candidate = MasteryDefinitionParser.parse(entries);
            manager.validateDefinitions(candidate);
            manager.registry().publish(candidate);
            // Reconciliation occurs on the next server tick, after tags have finished reloading.
            manager.definitionsChanged();
            LTSXLogicA.LOGGER.info("Mastery definitions loaded: {} masteries, {} runes, revision {}",
                    candidate.masteries().size(), candidate.runes().size(), manager.registry().revision());
        } catch (RuntimeException ex) {
            LTSXLogicA.LOGGER.error("Mastery reload rejected; retaining previous complete definitions", ex);
        }
    }
}
