package link.botwmcs.ltsxassistant.api.account;

/**
 * Lightweight Yggdrasil profile entry visible to UI.
 */
public record LittleSkinYggdrasilProfileSummary(
        String profileId,
        String profileName,
        String profileUuid
) {
    public LittleSkinYggdrasilProfileSummary {
        profileId = profileId == null ? "" : profileId;
        profileName = profileName == null ? "" : profileName;
        profileUuid = profileUuid == null ? "" : profileUuid;
    }
}
