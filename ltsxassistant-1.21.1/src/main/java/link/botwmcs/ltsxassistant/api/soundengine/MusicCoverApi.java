package link.botwmcs.ltsxassistant.api.soundengine;

import net.minecraft.resources.ResourceLocation;

/**
 * Cover texture query API for current music context.
 */
public interface MusicCoverApi {
    ResourceLocation currentCoverTexture();
}
