package link.botwmcs.ltsxlogica.heat.network;

import link.botwmcs.core.net.CoreNetwork;
import net.minecraft.server.level.ServerPlayer;

/**
 * Heat feature networking registration and helpers.
 */
public final class HeatNetworking {
    private HeatNetworking() {
    }

    public static void registerCorePayloads() {
        CoreNetwork.registerPlayToClient(
                HeatEntityTemperaturePayload.TYPE,
                HeatEntityTemperaturePayload.STREAM_CODEC,
                (payload, context) -> HeatClientSyncState.pushSyncedTemperature(payload.tempFixed())
        );
    }

    public static void sendEntityTemperature(ServerPlayer player, int tempFixed) {
        CoreNetwork.sendToPlayer(player, new HeatEntityTemperaturePayload(tempFixed));
    }
}
