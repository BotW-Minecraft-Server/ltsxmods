package link.botwmcs.core.client.debug;

import net.minecraft.client.Minecraft;

/**
 * Client-only bridge for opening debug screens from common networking code.
 */
public final class CoreClientDebugScreenAccess {
    private CoreClientDebugScreenAccess() {
    }

    public static void openNetworkingStatScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new CoreNetworkStatScreen()));
    }
}
