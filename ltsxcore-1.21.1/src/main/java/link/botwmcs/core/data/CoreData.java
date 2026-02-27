package link.botwmcs.core.data;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import link.botwmcs.core.util.CoreKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Unified data adapter layer.
 * <p>
 * Provides SavedData and player persistent NBT access in one place, leaving room for future DataComponent migration.
 */
public final class CoreData {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CoreData() {
    }

    public static void bootstrap() {
        LOGGER.info("{}CoreData initialized.", CoreKeys.LOG_PREFIX);
    }

    public static CoreGlobalSavedData getOrCreateSavedData(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("CoreGlobalSavedData is only available on server levels");
        }
        return getOrCreateSavedData(serverLevel);
    }

    public static CoreGlobalSavedData getOrCreateSavedData(ServerLevel serverLevel) {
        final ServerLevel overworld = serverLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(CoreGlobalSavedData.factory(), CoreGlobalSavedData.DATA_NAME);
    }

    /**
     * Returns a namespaced compound under the player's persistent data, creating it if absent.
     */
    public static CompoundTag getPlayerTag(Player player, String key) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");

        final CompoundTag root = getCorePlayerRoot(player);
        if (!root.contains(key, Tag.TAG_COMPOUND)) {
            root.put(key, new CompoundTag());
        }
        return root.getCompound(key);
    }

    /**
     * Writes a namespaced compound under player persistent data (defensive copy).
     */
    public static void putPlayerTag(Player player, String key, CompoundTag value) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        getCorePlayerRoot(player).put(key, value.copy());
    }

    public static boolean isDebugEnabled(MinecraftServer server) {
        return getOrCreateSavedData(server.overworld()).debugEnabled();
    }

    public static void setDebugEnabled(MinecraftServer server, boolean enabled) {
        getOrCreateSavedData(server.overworld()).setDebugEnabled(enabled);
    }

    private static CompoundTag getCorePlayerRoot(Player player) {
        final CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(CoreKeys.PLAYER_PERSISTENT_ROOT, Tag.TAG_COMPOUND)) {
            persistentData.put(CoreKeys.PLAYER_PERSISTENT_ROOT, new CompoundTag());
        }

        // TODO(1.21+): switch this bridge to DataComponent when the project migrates.
        return persistentData.getCompound(CoreKeys.PLAYER_PERSISTENT_ROOT);
    }
}
