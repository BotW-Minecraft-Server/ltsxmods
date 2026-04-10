package link.botwmcs.ltsxassistant.service.chat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.api.chat.AdvancedChatWindowService;
import net.minecraft.network.chat.Component;

/**
 * Placeholder implementation for assistant advanced chat window controls.
 */
public final class AssistantAdvancedChatWindowService implements AdvancedChatWindowService {
    @Override
    public List<ChatButtonSpec> resolveButtons(@Nullable UUID playerId, int permissionLevel) {
        List<ChatButtonSpec> buttons = new ArrayList<>(4);
        buttons.add(new ChatButtonSpec(ChatButton.CHAT, Component.literal("Chat")));
        buttons.add(new ChatButtonSpec(ChatButton.GROUP, Component.literal("Group")));
        buttons.add(new ChatButtonSpec(ChatButton.AGENT, Component.literal("Agent")));
        if (hasAdminAccess(playerId)) {
            buttons.add(new ChatButtonSpec(ChatButton.ADMIN, Component.literal("Admin")));
        }
        return List.copyOf(buttons);
    }

    @Override
    public void onButtonPressed(ChatButton button) {
        if (button == null) {
            return;
        }
        LTSXAssistant.LOGGER.info(
                "[ltsxassistant] Advanced chat placeholder button clicked: {}",
                button.name().toLowerCase(Locale.ROOT)
        );
    }

    private boolean hasAdminAccess(@Nullable UUID playerId) {
        return playerId != null && hasLuckPermsPermission(playerId, ADMIN_PERMISSION_NODE);
    }

    private boolean hasLuckPermsPermission(UUID playerId, String permissionNode) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);
            if (luckPerms == null) {
                return false;
            }

            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            if (userManager == null) {
                return false;
            }

            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, playerId);
            if (user == null) {
                return false;
            }

            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            if (cachedData == null) {
                return false;
            }

            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            if (permissionData == null) {
                return false;
            }

            Object tristate = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permissionNode);
            if (tristate == null) {
                return false;
            }

            Object asBoolean = tristate.getClass().getMethod("asBoolean").invoke(tristate);
            return asBoolean instanceof Boolean bool && bool;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
