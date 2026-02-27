package link.botwmcs.core.net;

import com.mojang.logging.LogUtils;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.data.CoreData;
import link.botwmcs.core.data.CoreGlobalSavedData;
import link.botwmcs.core.net.payload.DebugPingPayload;
import link.botwmcs.core.util.CoreIds;
import link.botwmcs.core.util.CoreKeys;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * Unified networking layer (server-first).
 * <p>
 * Thread/performance note: payload handlers enqueue work back to the main thread and keep logic lightweight.
 */
public final class CoreNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROTOCOL_VERSION = "1";

    private CoreNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(CoreNetwork::onRegisterPayloads);
        LOGGER.info("{}CoreNetwork registration listener attached.", CoreKeys.LOG_PREFIX);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(CoreIds.MOD_ID).versioned(PROTOCOL_VERSION);
        registrar.playToServer(DebugPingPayload.TYPE, DebugPingPayload.STREAM_CODEC, CoreNetwork::handleDebugPing);
        LOGGER.info("{}Registered payloads with protocol version {}.", CoreKeys.LOG_PREFIX, PROTOCOL_VERSION);
    }

    private static void handleDebugPing(DebugPingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            final CoreGlobalSavedData savedData = CoreData.getOrCreateSavedData(player.serverLevel());
            savedData.incrementDebugPingCounter();

            if (CoreConfig.enableDebug() || savedData.debugEnabled()) {
                LOGGER.debug("{}DebugPing from '{}' clientTime={} counter={}",
                        CoreKeys.LOG_PREFIX,
                        player.getGameProfile().getName(),
                        payload.clientTime(),
                        savedData.debugPingCounter());
            }
        });
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
