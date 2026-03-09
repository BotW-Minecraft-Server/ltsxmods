package link.botwmcs.core.net.neb;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Namespace/path index table for CoreNetworking payload ids.
 */
public final class CoreNebNamespaceIndexManager {
    private static final ArrayList<String> NAMESPACES = new ArrayList<>();
    private static final ArrayList<ArrayList<String>> PATHS = new ArrayList<>();
    private static final Object2IntMap<String> NAMESPACE_MAP = new Object2IntOpenHashMap<>();
    private static final HashMap<Integer, Object2IntMap<String>> PATH_MAPS = new HashMap<>();
    private static volatile boolean initialized = false;

    private CoreNebNamespaceIndexManager() {
    }

    public static synchronized void rebuild(Collection<ResourceLocation> types) {
        initialized = false;
        NAMESPACES.clear();
        PATHS.clear();
        NAMESPACE_MAP.clear();
        PATH_MAPS.clear();

        int namespaceIndex = 0;
        var sorted = new ArrayList<>(types);
        sorted.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        for (ResourceLocation type : sorted) {
            if (!NAMESPACE_MAP.containsKey(type.getNamespace())) {
                NAMESPACE_MAP.put(type.getNamespace(), namespaceIndex);
                NAMESPACES.add(type.getNamespace());
                PATHS.add(new ArrayList<>());
                namespaceIndex++;
            }

            final int currentNamespace = NAMESPACE_MAP.getInt(type.getNamespace());
            PATH_MAPS.compute(currentNamespace, (namespaceId, pathMap) -> {
                if (pathMap == null) {
                    pathMap = new Object2IntOpenHashMap<>();
                }
                pathMap.put(type.getPath(), pathMap.size());
                return pathMap;
            });
            PATHS.get(currentNamespace).add(type.getPath());
        }

        if (NAMESPACES.size() > 4096 || PATHS.stream().anyMatch(l -> l.size() > 4096)) {
            throw new IllegalStateException("Core NEB index overflow: max 4096 namespaces and 4096 paths per namespace.");
        }
        initialized = true;
    }

    public static synchronized int getNebIndex(ResourceLocation type) {
        if (!initialized || !contains(type)) {
            return 0;
        }
        int namespaceIndex = NAMESPACE_MAP.getInt(type.getNamespace());
        int pathIndex = PATH_MAPS.get(namespaceIndex).getInt(type.getPath());
        if (namespaceIndex < 256 && pathIndex < 256) {
            return 0xC0000000 | (namespaceIndex << 16) | (pathIndex << 8);
        }
        return 0x80000000 | (namespaceIndex << 12) | pathIndex;
    }

    public static synchronized ResourceLocation getIdentifier(int nebIndex, boolean tight) {
        if (!initialized) {
            return null;
        }
        int namespaceIndex;
        int pathIndex;
        if (tight) {
            namespaceIndex = (nebIndex & 0b11111111_00000000) >>> 8;
            pathIndex = nebIndex & 0b00000000_11111111;
        } else {
            namespaceIndex = (nebIndex & 0b11111111_11110000_00000000) >>> 12;
            pathIndex = nebIndex & 0b00000000_00001111_11111111;
        }
        if (namespaceIndex < 0 || namespaceIndex >= NAMESPACES.size()) {
            return null;
        }
        if (pathIndex < 0 || pathIndex >= PATHS.get(namespaceIndex).size()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(NAMESPACES.get(namespaceIndex), PATHS.get(namespaceIndex).get(pathIndex));
    }

    private static boolean contains(ResourceLocation type) {
        if (!NAMESPACE_MAP.containsKey(type.getNamespace())) {
            return false;
        }
        int namespaceId = NAMESPACE_MAP.getInt(type.getNamespace());
        Object2IntMap<String> map = PATH_MAPS.get(namespaceId);
        return map != null && map.containsKey(type.getPath());
    }
}
