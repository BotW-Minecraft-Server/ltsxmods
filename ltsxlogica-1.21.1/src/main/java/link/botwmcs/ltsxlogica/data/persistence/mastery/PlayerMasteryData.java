package link.botwmcs.ltsxlogica.data.persistence.mastery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import link.botwmcs.ltsxlogica.api.mastery.PlayerMasterySnapshot;
import link.botwmcs.ltsxlogica.api.mastery.RuneInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Keeps unknown fields/definitions for future restoration; future schemas are never overwritten. */
public final class PlayerMasteryData {
    public static final String KEY = "ltsxlogica:mastery";
    private final CompoundTag original;
    public long revision;
    public final Map<ResourceLocation, Progress> masteries = new HashMap<>();

    public static final class Progress {
        public int level = 1;
        public final Set<Integer> activated = new HashSet<>();
        public final Map<Integer, RuneInstance> bindings = new HashMap<>();
        private final Set<String> recognizedBindingKeys = new HashSet<>();
        public PlayerMasterySnapshot.Progress snapshot() { return new PlayerMasterySnapshot.Progress(level, activated, bindings); }
    }

    public PlayerMasteryData(CompoundTag tag) {
        original = tag.copy();
        if (tag.getInt("schema_version") > 1) throw new IllegalStateException("Unsupported future mastery playerData schema");
        revision = Math.max(0, tag.getLong("revision"));
        CompoundTag records = tag.getCompound("masteries");
        for (String key : records.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null || !records.contains(key, Tag.TAG_COMPOUND)) continue;
            CompoundTag record = records.getCompound(key);
            Progress progress = new Progress();
            progress.level = Math.max(1, Math.min(100, record.getInt("level")));
            for (int slot : record.getIntArray("activated")) if (slot >= 0 && slot < progress.level - 1) progress.activated.add(slot);
            CompoundTag bindings = record.getCompound("bindings");
            for (String slotKey : bindings.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    CompoundTag rune = bindings.getCompound(slotKey);
                    if (slot >= 0 && slot < progress.level - 1 && rune.hasUUID("instance")) {
                        progress.bindings.put(slot, new RuneInstance(ResourceLocation.parse(rune.getString("rune")), rune.getUUID("instance")));
                        progress.recognizedBindingKeys.add(slotKey);
                    }
                } catch (IllegalArgumentException ignored) { /* Original tag retains unrecognized entries. */ }
            }
            masteries.put(id, progress);
        }
    }

    public CompoundTag save() {
        CompoundTag root = original.copy();
        root.putInt("schema_version", 1);
        root.putLong("revision", revision);
        CompoundTag records = root.getCompound("masteries").copy();
        for (var entry : masteries.entrySet()) {
            String key = entry.getKey().toString();
            CompoundTag record = records.getCompound(key).copy();
            Progress progress = entry.getValue();
            record.putInt("level", progress.level);
            record.putIntArray("activated", progress.activated.stream().sorted().mapToInt(Integer::intValue).toArray());
            CompoundTag bindings = record.getCompound("bindings").copy();
            // Remove only recognized bindings, keeping malformed/future payloads for recovery.
            progress.recognizedBindingKeys.forEach(bindings::remove);
            for (var binding : progress.bindings.entrySet()) {
                CompoundTag rune = new CompoundTag();
                rune.putString("rune", binding.getValue().runeId().toString());
                rune.putUUID("instance", binding.getValue().instanceId());
                bindings.put(Integer.toString(binding.getKey()), rune);
            }
            record.put("bindings", bindings);
            records.put(key, record);
        }
        root.put("masteries", records);
        return root;
    }
}
