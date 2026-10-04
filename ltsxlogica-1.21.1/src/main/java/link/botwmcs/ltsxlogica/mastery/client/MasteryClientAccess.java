package link.botwmcs.ltsxlogica.mastery.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.botwmcs.core.net.CoreNetwork;
import link.botwmcs.ltsxlogica.mastery.network.MasteryActionPayload;
import link.botwmcs.ltsxlogica.mastery.network.MasterySyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Loaded reflectively only on the physical client. Never uses the integrated-server registry. */
public final class MasteryClientAccess {
    private static long transfer = -1;
    private static String[] chunks;
    private static int received;
    private static long requestId;
    private static JsonObject state;
    private MasteryClientAccess() {}
    public static void reset() { transfer = -1; chunks = null; received = 0; requestId = 0; state = null; }
    public static void accept(MasterySyncPayload payload) {
        if (payload.count() < 1 || payload.count() > MasterySyncPayload.MAX_CHUNKS || payload.index() < 0 || payload.index() >= payload.count()) return;
        if (payload.transferId() < transfer) return;
        if (payload.transferId() != transfer) { transfer = payload.transferId(); chunks = new String[payload.count()]; received = 0; }
        if (chunks == null || chunks.length != payload.count()) return;
        if (chunks[payload.index()] == null) received++;
        chunks[payload.index()] = payload.json();
        if (received != chunks.length) return;
        state = JsonParser.parseString(String.join("", chunks)).getAsJsonObject();
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.open() || minecraft.screen instanceof MasteryScreen) {
            MasteryScreen current = minecraft.screen instanceof MasteryScreen screen ? screen : null;
            minecraft.setScreen(new MasteryScreen(state, current == null ? null : current.selection()));
        }
        chunks = null;
    }
    public static void request(String action, ResourceLocation mastery, int slot, int inventorySlot) {
        if (state == null) return;
        CoreNetwork.sendToServer(new MasteryActionPayload(action, mastery, slot, inventorySlot,
                state.get("revision").getAsLong(), state.get("definition_revision").getAsLong(), ++requestId));
    }
}
