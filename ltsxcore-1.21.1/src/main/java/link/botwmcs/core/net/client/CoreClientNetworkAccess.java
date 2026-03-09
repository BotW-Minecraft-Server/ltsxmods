package link.botwmcs.core.net.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;

/**
 * Client-only network accessors for CoreNetworking.
 */
public final class CoreClientNetworkAccess {
    private CoreClientNetworkAccess() {
    }

    public static RegistryAccess registryAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            return mc.getConnection().registryAccess();
        }
        if (mc.level != null) {
            return mc.level.registryAccess();
        }
        return RegistryAccess.EMPTY;
    }
}

