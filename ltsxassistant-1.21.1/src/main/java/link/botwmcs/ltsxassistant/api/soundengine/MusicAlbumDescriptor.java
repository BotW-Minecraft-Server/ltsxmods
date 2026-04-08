package link.botwmcs.ltsxassistant.api.soundengine;

import net.minecraft.resources.ResourceLocation;

/**
 * Lightweight album entry visible to UI/API callers.
 */
public record MusicAlbumDescriptor(
        String albumId,
        String displayName,
        ResourceLocation coverTexture,
        int trackCount
) {
}