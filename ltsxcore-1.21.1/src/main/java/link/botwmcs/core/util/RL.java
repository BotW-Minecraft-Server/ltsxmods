package link.botwmcs.core.util;

import net.minecraft.resources.ResourceLocation;

/**
 * Short utility for building ResourceLocation values in the core namespace.
 */
public final class RL {
    private RL() {
    }

    public static ResourceLocation rl(String path) {
        return CoreIds.id(path);
    }
}