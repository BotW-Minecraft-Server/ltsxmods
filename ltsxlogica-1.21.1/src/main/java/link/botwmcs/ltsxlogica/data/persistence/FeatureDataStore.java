package link.botwmcs.ltsxlogica.data.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * Generic facade for feature payload persistence.
 *
 * Any feature can reuse this by choosing a unique feature key and owning its own payload schema.
 */
public final class FeatureDataStore {
    private FeatureDataStore() {
    }

    public static CompoundTag loadFeatureTag(ServerLevel level, String featureKey) {
        return FeatureSavedData.get(level).getFeaturePayload(featureKey);
    }

    public static void saveFeatureTag(ServerLevel level, String featureKey, CompoundTag payload) {
        FeatureSavedData.get(level).setFeaturePayload(featureKey, payload);
    }

    public static void removeFeatureTag(ServerLevel level, String featureKey) {
        FeatureSavedData.get(level).removeFeaturePayload(featureKey);
    }
}
