package link.botwmcs.ltsxlogica.api.mastery;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public record PlayerMasterySnapshot(UUID playerId, long stateRevision, long definitionRevision,
                                     Map<ResourceLocation, Progress> masteries) {
    public PlayerMasterySnapshot { masteries = Map.copyOf(masteries); }
    public record Progress(int level, Set<Integer> activatedSlots, Map<Integer, RuneInstance> bindings) {
        public Progress { activatedSlots = Set.copyOf(activatedSlots); bindings = Map.copyOf(bindings); }
        /** Zero-based slots: level 2 unlocks slot 0; level 100 unlocks slot 98. */
        public int unlockedSlots() { return Math.max(0, level - 1); }
    }
}
