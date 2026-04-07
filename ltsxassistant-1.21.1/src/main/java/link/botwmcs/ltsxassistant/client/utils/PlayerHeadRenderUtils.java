package link.botwmcs.ltsxassistant.client.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared helper for current-user player head rendering and head texture generation.
 */
public final class PlayerHeadRenderUtils {
    private static final int HEAD_TEXTURE_LAYOUT_VERSION = 2;
    private static final int DEFAULT_HEAD_SIZE_PX = 16;
    private static final int FIZZY_DYNAMIC_TEXTURE_SIZE_PX = 16;
    private static final int SKIN_HEAD_SIZE_PX = 8;
    private static final int SKIN_HEAD_U = 8;
    private static final int SKIN_HEAD_V = 8;
    private static final int SKIN_HAT_U = 40;
    private static final int SKIN_HAT_V = 8;
    private static final Map<HeadTextureCacheKey, ResourceLocation> HEAD_TEXTURE_CACHE = new ConcurrentHashMap<>();

    private PlayerHeadRenderUtils() {
    }

    public static String currentUsername() {
        var user = Minecraft.getInstance().getUser();
        return user == null ? "Player" : user.getName();
    }

    public static PlayerSkin currentPlayerSkin() {
        Minecraft minecraft = Minecraft.getInstance();
        var user = minecraft.getUser();
        if (user == null) {
            return null;
        }
        GameProfile profile = new GameProfile(user.getProfileId(), user.getName());
        return minecraft.getSkinManager().getInsecureSkin(profile);
    }

    public static void drawCurrentPlayerHead(GuiGraphics guiGraphics, int x, int y, int sizePx) {
        PlayerSkin skin = currentPlayerSkin();
        if (skin != null) {
            PlayerFaceRenderer.draw(guiGraphics, skin, x, y, sizePx);
        }
    }

    public static ResourceLocation currentPlayerHeadTexture() {
        return currentPlayerHeadTexture(DEFAULT_HEAD_SIZE_PX);
    }

    public static ResourceLocation currentPlayerHeadTexture(int sizePx) {
        int targetSizePx = Math.max(1, sizePx);
        PlayerSkin skin = currentPlayerSkin();
        if (skin == null) {
            return null;
        }
        ResourceLocation skinTexture = skin.texture();
        if (skinTexture == null) {
            return null;
        }
        HeadTextureCacheKey cacheKey = new HeadTextureCacheKey(skinTexture, targetSizePx, HEAD_TEXTURE_LAYOUT_VERSION);
        return HEAD_TEXTURE_CACHE.computeIfAbsent(
                cacheKey,
                key -> createHeadTextureFromSkin(key.skinTexture(), key.sizePx())
        );
    }

    private static ResourceLocation createHeadTextureFromSkin(ResourceLocation skinTexture, int sizePx) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureManager textureManager = minecraft.getTextureManager();
        AbstractTexture sourceTexture = textureManager.getTexture(skinTexture);
        sourceTexture.bind();

        NativeImage source = new NativeImage(64, 64, false);
        source.downloadTexture(0, false);

        // Fizzy resolves dynamic textures as 16x16 fallback size, so we keep a 16x16 canvas and
        // place the requested head size in the top-left corner to preserve full-head sampling.
        int targetHeadSizePx = Math.max(1, Math.min(sizePx, FIZZY_DYNAMIC_TEXTURE_SIZE_PX));
        NativeImage head = new NativeImage(FIZZY_DYNAMIC_TEXTURE_SIZE_PX, FIZZY_DYNAMIC_TEXTURE_SIZE_PX, true);
        copyHead(source, head, targetHeadSizePx);
        source.close();

        DynamicTexture dynamicTexture = new DynamicTexture(head);
        dynamicTexture.upload();
        String idPath = "player_head/v" + HEAD_TEXTURE_LAYOUT_VERSION + "/" + Integer.toHexString(skinTexture.hashCode()) + "_" + targetHeadSizePx;
        return textureManager.register(idPath, dynamicTexture);
    }

    private static void copyHead(NativeImage source, NativeImage out, int sizePx) {
        for (int y = 0; y < sizePx; y++) {
            int srcY = (y * SKIN_HEAD_SIZE_PX) / sizePx;
            for (int x = 0; x < sizePx; x++) {
                int srcX = (x * SKIN_HEAD_SIZE_PX) / sizePx;
                int base = source.getPixelRGBA(SKIN_HEAD_U + srcX, SKIN_HEAD_V + srcY);
                int hat = source.getPixelRGBA(SKIN_HAT_U + srcX, SKIN_HAT_V + srcY);
                out.setPixelRGBA(x, y, blend(base, hat));
            }
        }
    }

    private static int blend(int base, int top) {
        return ((top >>> 24) & 0xFF) == 0 ? base : top;
    }

    private record HeadTextureCacheKey(ResourceLocation skinTexture, int sizePx, int layoutVersion) {
    }
}
