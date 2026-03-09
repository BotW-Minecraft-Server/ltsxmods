package link.botwmcs.core.net.neb;

import java.util.Set;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.net.payload.CoreNebBatchPayload;
import link.botwmcs.core.net.payload.CoreNebDirectPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Compatibility blacklist for Core NEB transport.
 */
public final class CoreNebBlacklist {
    private static final Set<String> COMMON_BYPASS_TYPES = Set.of(
            CoreNebBatchPayload.TYPE.id().toString(),
            CoreNebDirectPayload.TYPE.id().toString()
    );

    private CoreNebBlacklist() {
    }

    public static boolean shouldBypass(ResourceLocation type) {
        String id = type.toString();
        if (COMMON_BYPASS_TYPES.contains(id)) {
            return true;
        }
        if (!CoreConfig.nebCompatibleMode()) {
            return false;
        }
        return CoreConfig.nebBlackList().contains(id);
    }
}
