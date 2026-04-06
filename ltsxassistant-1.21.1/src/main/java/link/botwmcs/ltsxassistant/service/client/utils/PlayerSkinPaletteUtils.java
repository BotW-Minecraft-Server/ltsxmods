package link.botwmcs.ltsxassistant.service.client.utils;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Utility for deriving a compact bright/dark palette from the current player's skin.
 */
public final class PlayerSkinPaletteUtils {
    private static final int SKIN_SIZE_PX = 64;
    private static final int SAMPLE_STEP_PX = 2;
    private static final int MIN_ALPHA_SAMPLE = 24;
    private static final int PALETTE_VERSION = 1;
    private static final int DEFAULT_BASE_COLOR = argb(255, 74, 93, 132);
    private static final SkinPalette DEFAULT_PALETTE = paletteFromBase(DEFAULT_BASE_COLOR);
    private static final Map<PaletteCacheKey, SkinPalette> PALETTE_CACHE = new ConcurrentHashMap<>();

    private PlayerSkinPaletteUtils() {
    }

    public static SkinPalette currentPlayerPalette() {
        PlayerSkin skin = PlayerHeadRenderUtils.currentPlayerSkin();
        if (skin == null || skin.texture() == null) {
            return DEFAULT_PALETTE;
        }
        PaletteCacheKey key = new PaletteCacheKey(skin.texture(), PALETTE_VERSION);
        return PALETTE_CACHE.computeIfAbsent(key, next -> computePalette(next.skinTexture()));
    }

    private static SkinPalette computePalette(ResourceLocation skinTexture) {
        try {
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            AbstractTexture sourceTexture = textureManager.getTexture(skinTexture);
            sourceTexture.bind();
            NativeImage source = new NativeImage(SKIN_SIZE_PX, SKIN_SIZE_PX, false);
            try {
                source.downloadTexture(0, false);
                int baseColor = averageSkinColor(source);
                return paletteFromBase(baseColor);
            } finally {
                source.close();
            }
        } catch (Exception ignored) {
            return DEFAULT_PALETTE;
        }
    }

    private static int averageSkinColor(NativeImage source) {
        long sumR = 0L;
        long sumG = 0L;
        long sumB = 0L;
        long sumWeight = 0L;

        for (int y = 0; y < SKIN_SIZE_PX; y += SAMPLE_STEP_PX) {
            for (int x = 0; x < SKIN_SIZE_PX; x += SAMPLE_STEP_PX) {
                int alpha = source.getLuminanceOrAlpha(x, y) & 0xFF;
                if (alpha < MIN_ALPHA_SAMPLE) {
                    continue;
                }
                int weight = alpha;
                int r = source.getRedOrLuminance(x, y) & 0xFF;
                int g = source.getGreenOrLuminance(x, y) & 0xFF;
                int b = source.getBlueOrLuminance(x, y) & 0xFF;
                sumR += (long) r * weight;
                sumG += (long) g * weight;
                sumB += (long) b * weight;
                sumWeight += weight;
            }
        }

        if (sumWeight <= 0L) {
            return DEFAULT_BASE_COLOR;
        }

        int r = (int) (sumR / sumWeight);
        int g = (int) (sumG / sumWeight);
        int b = (int) (sumB / sumWeight);
        return argb(255, r, g, b);
    }

    private static SkinPalette paletteFromBase(int baseColor) {
        int lightColor = shiftLightness(baseColor, 0.18f);
        int lighterColor = shiftLightness(baseColor, 0.34f);
        int darkColor = shiftLightness(baseColor, -0.26f);
        int darkerColor = shiftLightness(baseColor, -0.40f);

        int outlineColor = withAlpha(lighterColor, 0xE5);
        int fillColor = withAlpha(darkColor, 0xCC);
        int textColor = chooseTextColor(darkColor);

        return new SkinPalette(
                baseColor,
                lightColor,
                lighterColor,
                darkColor,
                darkerColor,
                outlineColor,
                fillColor,
                textColor
        );
    }

    private static int shiftLightness(int color, float amount) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        if (amount >= 0.0f) {
            r = clampChannel(r + Math.round((255 - r) * amount));
            g = clampChannel(g + Math.round((255 - g) * amount));
            b = clampChannel(b + Math.round((255 - b) * amount));
        } else {
            float scale = 1.0f + amount;
            r = clampChannel(Math.round(r * scale));
            g = clampChannel(Math.round(g * scale));
            b = clampChannel(Math.round(b * scale));
        }
        return argb(255, r, g, b);
    }

    private static int chooseTextColor(int backgroundColor) {
        int r = (backgroundColor >> 16) & 0xFF;
        int g = (backgroundColor >> 8) & 0xFF;
        int b = backgroundColor & 0xFF;
        float luminance = (0.299f * r) + (0.587f * g) + (0.114f * b);
        return luminance >= 150.0f ? 0xFF111111 : 0xFFFFFFFF;
    }

    private static int withAlpha(int rgbColor, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgbColor & 0x00FFFFFF);
    }

    private static int argb(int alpha, int r, int g, int b) {
        return ((alpha & 0xFF) << 24)
                | ((r & 0xFF) << 16)
                | ((g & 0xFF) << 8)
                | (b & 0xFF);
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public record SkinPalette(
            int baseColor,
            int lightColor,
            int lighterColor,
            int darkColor,
            int darkerColor,
            int outlineColor,
            int fillColor,
            int textColor
    ) {
    }

    private record PaletteCacheKey(ResourceLocation skinTexture, int paletteVersion) {
    }
}
