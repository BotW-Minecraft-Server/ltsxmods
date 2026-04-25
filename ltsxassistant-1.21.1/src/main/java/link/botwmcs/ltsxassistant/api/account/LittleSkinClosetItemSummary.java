package link.botwmcs.ltsxassistant.api.account;

/**
 * Lightweight closet entry for LittleSkin UI binding.
 */
public record LittleSkinClosetItemSummary(
        String closetItemId,
        String textureId,
        String displayName,
        String textureType,
        String textureUrl
) {
    public LittleSkinClosetItemSummary {
        closetItemId = closetItemId == null ? "" : closetItemId;
        textureId = textureId == null ? "" : textureId;
        displayName = displayName == null ? "" : displayName;
        textureType = textureType == null ? "" : textureType;
        textureUrl = textureUrl == null ? "" : textureUrl;
    }
}
