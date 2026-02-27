package link.botwmcs.core.data;

import link.botwmcs.core.util.CoreIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Global SavedData example for core-wide state, independent from specific business modules.
 */
public final class CoreGlobalSavedData extends SavedData {
    public static final String DATA_NAME = CoreIds.MOD_ID + "_global";
    private static final int CURRENT_DATA_VERSION = 1;

    private int dataVersion = CURRENT_DATA_VERSION;
    private boolean debugEnabled;
    private long debugPingCounter;

    public static Factory<CoreGlobalSavedData> factory() {
        return new Factory<>(CoreGlobalSavedData::new, CoreGlobalSavedData::load);
    }

    private static CoreGlobalSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        final CoreGlobalSavedData data = new CoreGlobalSavedData();
        data.dataVersion = tag.getInt("dataVersion");
        if (data.dataVersion <= 0) {
            data.dataVersion = CURRENT_DATA_VERSION;
        }
        data.debugEnabled = tag.getBoolean("debugEnabled");
        data.debugPingCounter = tag.getLong("debugPingCounter");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("dataVersion", this.dataVersion);
        tag.putBoolean("debugEnabled", this.debugEnabled);
        tag.putLong("debugPingCounter", this.debugPingCounter);
        return tag;
    }

    public int dataVersion() {
        return dataVersion;
    }

    public boolean debugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        if (this.debugEnabled != debugEnabled) {
            this.debugEnabled = debugEnabled;
            setDirty();
        }
    }

    public long debugPingCounter() {
        return debugPingCounter;
    }

    public void incrementDebugPingCounter() {
        this.debugPingCounter++;
        setDirty();
    }
}
