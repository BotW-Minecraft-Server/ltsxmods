package link.botwmcs.ltsxlogica.heat.network;

import link.botwmcs.core.net.CoreNetwork;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Heat feature networking registration and helpers.
 */
public final class HeatNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private HeatNetworking() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(LTSXLogicA.MODID).versioned(PROTOCOL_VERSION);
        registrar.playToClient(
                HeatEntityTemperaturePayload.TYPE,
                HeatEntityTemperaturePayload.STREAM_CODEC,
                (payload, context) -> HeatClientSyncState.pushSyncedTemperature(payload.tempFixed())
        );
    }

    public static void sendEntityTemperature(ServerPlayer player, int tempFixed) {
        CoreNetwork.sendToPlayer(player, new HeatEntityTemperaturePayload(tempFixed));
    }
}
