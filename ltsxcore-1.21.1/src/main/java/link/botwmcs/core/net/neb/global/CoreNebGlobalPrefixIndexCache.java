package link.botwmcs.core.net.neb.global;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Weak cache from payload codec map identity to prefix index table.
 */
public final class CoreNebGlobalPrefixIndexCache {
    private static final Map<Map<ResourceLocation, ?>, CoreNebGlobalPrefixIndex> CACHE = new WeakHashMap<>();

    private CoreNebGlobalPrefixIndexCache() {
    }

    public static synchronized CoreNebGlobalPrefixIndex get(Map<ResourceLocation, ?> payloadCodecMap) {
        return CACHE.computeIfAbsent(payloadCodecMap, map -> new CoreNebGlobalPrefixIndex(map.keySet()));
    }
}
