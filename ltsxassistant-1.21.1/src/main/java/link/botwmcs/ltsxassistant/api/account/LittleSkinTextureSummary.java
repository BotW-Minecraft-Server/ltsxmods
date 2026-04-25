package link.botwmcs.ltsxassistant.api.account;

/**
 * Lightweight texture/material entry for LittleSkin UI binding.
 */
public record LittleSkinTextureSummary(
        String textureId,
        String displayName,
        String textureType,
        String textureUrl,
        boolean active
) {
    public LittleSkinTextureSummary {
        textureId = textureId == null ? "" : textureId;
        displayName = displayName == null ? "" : displayName;
        textureType = textureType == null ? "" : textureType;
        textureUrl = textureUrl == null ? "" : textureUrl;
    }
}
