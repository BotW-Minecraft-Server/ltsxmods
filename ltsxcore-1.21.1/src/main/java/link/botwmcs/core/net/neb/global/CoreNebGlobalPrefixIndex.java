package link.botwmcs.core.net.neb.global;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Prefix index table bound to a payload codec map snapshot.
 */
public final class CoreNebGlobalPrefixIndex {
    private final ArrayList<String> namespaces = new ArrayList<>();
    private final ArrayList<ArrayList<String>> paths = new ArrayList<>();
    private final Object2IntMap<String> namespaceMap = new Object2IntOpenHashMap<>();
    private final HashMap<Integer, Object2IntMap<String>> pathMaps = new HashMap<>();

    public CoreNebGlobalPrefixIndex(Collection<ResourceLocation> types) {
        namespaceMap.defaultReturnValue(-1);
        rebuild(types);
    }

    private void rebuild(Collection<ResourceLocation> types) {
        int namespaceIndex = 0;
        ArrayList<ResourceLocation> sorted = new ArrayList<>(types);
        sorted.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        for (ResourceLocation type : sorted) {
            if (!namespaceMap.containsKey(type.getNamespace())) {
                namespaceMap.put(type.getNamespace(), namespaceIndex);
                namespaces.add(type.getNamespace());
                paths.add(new ArrayList<>());
                namespaceIndex++;
            }

            int currentNamespace = namespaceMap.getInt(type.getNamespace());
            pathMaps.compute(currentNamespace, (ignored, pathMap) -> {
                if (pathMap == null) {
                    pathMap = new Object2IntOpenHashMap<>();
                    pathMap.defaultReturnValue(-1);
                }
                pathMap.put(type.getPath(), pathMap.size());
                return pathMap;
            });
            paths.get(currentNamespace).add(type.getPath());
        }
    }

    public int getNebIndex(ResourceLocation type) {
        if (!contains(type)) {
            return 0;
        }
        int namespaceIndex = namespaceMap.getInt(type.getNamespace());
        int pathIndex = pathMaps.get(namespaceIndex).getInt(type.getPath());
        if (namespaceIndex < 256 && pathIndex < 256) {
            return 0xC0000000 | (namespaceIndex << 16) | (pathIndex << 8);
        }
        return 0x80000000 | (namespaceIndex << 12) | pathIndex;
    }

    public ResourceLocation getIdentifier(int nebIndex, boolean tight) {
        int namespaceIndex;
        int pathIndex;
        if (tight) {
            namespaceIndex = (nebIndex & 0b11111111_00000000) >>> 8;
            pathIndex = nebIndex & 0b00000000_11111111;
        } else {
            namespaceIndex = (nebIndex & 0b11111111_11110000_00000000) >>> 12;
            pathIndex = nebIndex & 0b00000000_00001111_11111111;
        }

        if (namespaceIndex < 0 || namespaceIndex >= namespaces.size()) {
            return null;
        }
        if (pathIndex < 0 || pathIndex >= paths.get(namespaceIndex).size()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(namespaces.get(namespaceIndex), paths.get(namespaceIndex).get(pathIndex));
    }

    private boolean contains(ResourceLocation type) {
        int namespaceId = namespaceMap.getInt(type.getNamespace());
        if (namespaceId < 0) {
            return false;
        }
        Object2IntMap<String> map = pathMaps.get(namespaceId);
        return map != null && map.getInt(type.getPath()) >= 0;
    }
}
