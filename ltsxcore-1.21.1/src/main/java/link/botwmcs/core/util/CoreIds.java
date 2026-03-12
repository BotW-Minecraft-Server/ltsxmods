package link.botwmcs.core.util;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared identifier constants and ResourceLocation helpers.
 */
public final class CoreIds {
    public static final String MOD_ID = "ltsxcore";
    public static final String ROOT_COMMAND = "ltsx";

    private CoreIds() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
