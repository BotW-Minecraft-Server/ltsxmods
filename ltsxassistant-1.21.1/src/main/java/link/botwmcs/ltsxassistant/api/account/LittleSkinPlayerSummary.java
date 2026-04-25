package link.botwmcs.ltsxassistant.api.account;

/**
 * Lightweight player/role entry for LittleSkin UI binding.
 */
public record LittleSkinPlayerSummary(
        String playerId,
        String displayName,
        String playerUuid,
        int textureCount
) {
    public LittleSkinPlayerSummary {
        playerId = playerId == null ? "" : playerId;
        displayName = displayName == null ? "" : displayName;
        playerUuid = playerUuid == null ? "" : playerUuid;
    }
}
