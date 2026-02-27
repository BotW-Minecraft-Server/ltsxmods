package link.botwmcs.ltsxlogica.data.persistence;

import java.util.HashMap;
import java.util.Map;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Generic mod-level saved data container for feature sub-systems.
 *
 * Each feature stores one payload CompoundTag by feature key:
 * - key example: "heat"
 * - payload: feature-owned schema/version
 */
public final class FeatureSavedData extends SavedData {
    public static final String FILE_ID = LTSXLogicA.MODID + "_features";
    private static final String TAG_FEATURES = "features";

    private final Map<String, CompoundTag> featurePayloads = new HashMap<>();

    public static SavedData.Factory<FeatureSavedData> factory() {
        return new SavedData.Factory<>(FeatureSavedData::new, FeatureSavedData::load);
    }

    public static FeatureSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static FeatureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FeatureSavedData data = new FeatureSavedData();
        if (!tag.contains(TAG_FEATURES, Tag.TAG_COMPOUND)) {
            return data;
        }

        CompoundTag featuresTag = tag.getCompound(TAG_FEATURES);
        for (String featureKey : featuresTag.getAllKeys()) {
            if (featuresTag.contains(featureKey, Tag.TAG_COMPOUND)) {
                data.featurePayloads.put(featureKey, featuresTag.getCompound(featureKey).copy());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag featuresTag = new CompoundTag(this.featurePayloads.size());
        for (Map.Entry<String, CompoundTag> entry : this.featurePayloads.entrySet()) {
            featuresTag.put(entry.getKey(), entry.getValue().copy());
        }
        tag.put(TAG_FEATURES, featuresTag);
        return tag;
    }

    public boolean hasFeaturePayload(String featureKey) {
        return this.featurePayloads.containsKey(featureKey);
    }

    public CompoundTag getFeaturePayload(String featureKey) {
        CompoundTag payload = this.featurePayloads.get(featureKey);
        return payload == null ? new CompoundTag() : payload.copy();
    }

    public void setFeaturePayload(String featureKey, CompoundTag payload) {
        this.featurePayloads.put(featureKey, payload.copy());
        this.setDirty();
    }

    public void removeFeaturePayload(String featureKey) {
        if (this.featurePayloads.remove(featureKey) != null) {
            this.setDirty();
        }
    }
}
