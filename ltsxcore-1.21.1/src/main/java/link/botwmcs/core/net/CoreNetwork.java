package link.botwmcs.core.net;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.data.CoreData;
import link.botwmcs.core.data.CoreGlobalSavedData;
import link.botwmcs.core.net.neb.CoreNebAggregationManager;
import link.botwmcs.core.net.neb.CoreNebBlacklist;
import link.botwmcs.core.net.neb.CoreNebNamespaceIndexManager;
import link.botwmcs.core.net.neb.CoreNebPacketPrefixHelper;
import link.botwmcs.core.net.payload.DebugPingPayload;
import link.botwmcs.core.net.payload.CoreNebBatchPayload;
import link.botwmcs.core.net.payload.CoreNebDirectPayload;
import link.botwmcs.core.net.payload.CoreNebGlobalBatchPayload;
import link.botwmcs.core.net.payload.OpenNetworkingStatScreenPayload;
import link.botwmcs.core.net.stat.CoreNebPacketFlowStat;
import link.botwmcs.core.net.stat.CoreNebTrafficStat;
import link.botwmcs.core.util.CoreIds;
import link.botwmcs.core.util.CoreKeys;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * Core-owned networking layer.
 * <p>
 * Vanilla networking stays untouched. Core payloads are carried on a dedicated core transport and can be optimized by
 * NEB internals.
 */
public final class CoreNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String VANILLA_PROTOCOL_VERSION = "1";
    private static final Map<ResourceLocation, CorePayloadRegistration<?>> CORE_REGISTRATIONS = new ConcurrentHashMap<>();
    private static final Object REGISTRATION_LOCK = new Object();
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);
    private static final CoreConnection CLIENT_CONNECTION = new CoreConnection() {
        @Override
        public PacketFlow flow() {
            return PacketFlow.SERVERBOUND;
        }

        @Override
        public void send(CorePacketPayload payload) {
            CoreNetwork.sendToServer(payload);
        }

        @Override
        public void sendVanilla(CustomPacketPayload payload) {
            CoreNetwork.sendToServer(payload);
        }
    };

    private CoreNetwork() {
    }

    public static void register(IEventBus modBus) {
        bootstrapCorePayloads();
        modBus.addListener(CoreNetwork::onRegisterPayloads);
        LOGGER.info("{}CoreNetwork registration listener attached.", CoreKeys.LOG_PREFIX);
    }

    public static CoreConnection playerConnection(ServerPlayer player) {
        return new CoreConnection() {
            @Override
            public PacketFlow flow() {
                return PacketFlow.CLIENTBOUND;
            }

            @Override
            public void send(CorePacketPayload payload) {
                CoreNetwork.sendToPlayer(player, payload);
            }

            @Override
            public void sendVanilla(CustomPacketPayload payload) {
                CoreNetwork.sendToPlayer(player, payload);
            }
        };
    }

    public static CoreConnection clientConnection() {
        return CLIENT_CONNECTION;
    }

    public static <T extends CorePacketPayload> void registerPlayToServer(
            CorePayloadType<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            CorePayloadHandler<T> handler
    ) {
        registerInternal(type, codec, handler, null);
    }

    public static <T extends CorePacketPayload> void registerPlayToClient(
            CorePayloadType<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            CorePayloadHandler<T> handler
    ) {
        registerInternal(type, codec, null, handler);
    }

    public static <T extends CorePacketPayload> void registerPlayBidirectional(
            CorePayloadType<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            CorePayloadHandler<T> serverHandler,
            CorePayloadHandler<T> clientHandler
    ) {
        registerInternal(type, codec, serverHandler, clientHandler);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(CoreIds.MOD_ID)
                .versioned(VANILLA_PROTOCOL_VERSION)
                .executesOn(HandlerThread.NETWORK);
        registrar.playBidirectional(CoreNebBatchPayload.TYPE, CoreNebBatchPayload.STREAM_CODEC, CoreNetwork::handleNebBatch);
        registrar.playBidirectional(CoreNebDirectPayload.TYPE, CoreNebDirectPayload.STREAM_CODEC, CoreNetwork::handleNebDirect);
        registrar.playBidirectional(CoreNebGlobalBatchPayload.TYPE, CoreNebGlobalBatchPayload.STREAM_CODEC, CoreNetwork::handleNebGlobalBatch);
        CoreNebAggregationManager.init();
        LOGGER.info("{}Registered core transport payloads with protocol version {}.", CoreKeys.LOG_PREFIX, VANILLA_PROTOCOL_VERSION);
    }

    private static <T extends CorePacketPayload> void registerInternal(
            CorePayloadType<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            CorePayloadHandler<T> serverHandler,
            CorePayloadHandler<T> clientHandler
    ) {
        synchronized (REGISTRATION_LOCK) {
            @SuppressWarnings("unchecked")
            CorePayloadRegistration<T> registration = (CorePayloadRegistration<T>) CORE_REGISTRATIONS.compute(type.id(), (id, existing) -> {
                if (existing == null) {
                    return new CorePayloadRegistration<>(type, codec);
                }
                if (existing.codec != codec) {
                    throw new IllegalStateException("Core payload codec mismatch for " + type.id());
                }
                return existing;
            });

            if (serverHandler != null) {
                if (registration.serverHandler != null) {
                    throw new IllegalStateException("Duplicate server handler registration for " + type.id());
                }
                registration.serverHandler = serverHandler;
            }
            if (clientHandler != null) {
                if (registration.clientHandler != null) {
                    throw new IllegalStateException("Duplicate client handler registration for " + type.id());
                }
                registration.clientHandler = clientHandler;
            }
            CoreNebNamespaceIndexManager.rebuild(CORE_REGISTRATIONS.keySet());
        }
    }

    private static void bootstrapCorePayloads() {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }
        registerPlayToServer(DebugPingPayload.TYPE, DebugPingPayload.STREAM_CODEC, CoreNetwork::handleDebugPing);
        registerPlayToClient(
                OpenNetworkingStatScreenPayload.TYPE,
                OpenNetworkingStatScreenPayload.STREAM_CODEC,
                CoreNetwork::handleOpenNetworkingStatScreen
        );
    }

    private static void handleDebugPing(DebugPingPayload payload, CorePayloadContext context) {
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

    private static void handleOpenNetworkingStatScreen(OpenNetworkingStatScreenPayload payload, CorePayloadContext context) {
        if (!payload.open()) {
            return;
        }
        context.enqueueWork(CoreNetwork::openClientNetworkingStatScreen);
    }

    public static void sendToPlayer(ServerPlayer player, CorePacketPayload payload) {
        CorePayloadRegistration<?> registration = requireRegistration(payload.type().id(), PacketFlow.CLIENTBOUND);
        byte[] encoded = encodePayload(registration, payload, player.level().registryAccess());
        CoreNebAggregationManager.queueToPlayer(
                player,
                payload.type().id(),
                encoded,
                CoreNebBlacklist.shouldBypass(payload.type().id())
        );
    }

    public static void sendToServer(CorePacketPayload payload) {
        CorePayloadRegistration<?> registration = requireRegistration(payload.type().id(), PacketFlow.SERVERBOUND);
        RegistryAccess registryAccess = resolveClientRegistryAccess();
        byte[] encoded = encodePayload(registration, payload, registryAccess);
        CoreNebAggregationManager.queueToServer(
                payload.type().id(),
                encoded,
                CoreNebBlacklist.shouldBypass(payload.type().id())
        );
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void handleNebDirect(CoreNebDirectPayload payload, IPayloadContext context) {
        CoreNebTrafficStat.inBaked(payload.data().length);
        CoreNebTrafficStat.inRaw(payload.data().length);
        dispatchCorePayload(payload.coreType(), payload.data(), context);
    }

    private static void handleNebGlobalBatch(CoreNebGlobalBatchPayload payload, IPayloadContext context) {
        payload.handle(context);
    }

    private static void handleNebBatch(CoreNebBatchPayload payload, IPayloadContext context) {
        CoreNebTrafficStat.inBaked(payload.data().length);
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
        FriendlyByteBuf raw = data;

        try {
            boolean compressed = data.readBoolean();
            if (compressed) {
                int rawSize = data.readVarInt();
                raw = new FriendlyByteBuf(link.botwmcs.core.net.neb.CoreNebZstdHelper.decompress(context.connection(), data, rawSize));
            }
            CoreNebTrafficStat.inRaw(raw.readableBytes());

            while (raw.readableBytes() > 0) {
                ResourceLocation type = CoreNebPacketPrefixHelper.getType(raw);
                int size = raw.readVarInt();
                CoreNebPacketFlowStat.in(type, size);
                byte[] bytes = new byte[size];
                raw.readBytes(bytes);
                dispatchCorePayload(type, bytes, context);
            }
        } catch (Exception e) {
            LOGGER.error("{}Failed to decode core NEB batch payload", CoreKeys.LOG_PREFIX, e);
        } finally {
            raw.release();
            if (raw != data) {
                data.release();
            }
        }
    }

    private static void dispatchCorePayload(ResourceLocation type, byte[] bytes, IPayloadContext context) {
        CorePayloadRegistration<?> registration = CORE_REGISTRATIONS.get(type);
        if (registration == null) {
            LOGGER.warn("{}No CoreNetworking registration for payload {}", CoreKeys.LOG_PREFIX, type);
            return;
        }

        CorePacketPayload decoded;
        try {
            decoded = decodePayload(registration, bytes, context.player().level().registryAccess());
        } catch (Exception e) {
            LOGGER.error("{}Failed to decode core payload {}", CoreKeys.LOG_PREFIX, type, e);
            return;
        }

        CorePayloadContext coreContext = new CorePayloadContext(context);
        if (context.flow() == PacketFlow.SERVERBOUND) {
            if (registration.serverHandler == null) {
                LOGGER.warn("{}No server handler for core payload {}", CoreKeys.LOG_PREFIX, type);
                return;
            }
            invokeHandler(registration.serverHandler, decoded, coreContext);
        } else if (context.flow() == PacketFlow.CLIENTBOUND) {
            if (registration.clientHandler == null) {
                LOGGER.warn("{}No client handler for core payload {}", CoreKeys.LOG_PREFIX, type);
                return;
            }
            invokeHandler(registration.clientHandler, decoded, coreContext);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CorePacketPayload> void invokeHandler(CorePayloadHandler<T> handler, CorePacketPayload payload, CorePayloadContext context) {
        handler.handle((T) payload, context);
    }

    private static CorePayloadRegistration<?> requireRegistration(ResourceLocation type, PacketFlow sendFlow) {
        CorePayloadRegistration<?> registration = CORE_REGISTRATIONS.get(type);
        if (registration == null) {
            throw new IllegalStateException("Core payload not registered: " + type);
        }
        if (sendFlow == PacketFlow.CLIENTBOUND && registration.clientHandler == null) {
            throw new IllegalStateException("Core payload has no client handler: " + type);
        }
        if (sendFlow == PacketFlow.SERVERBOUND && registration.serverHandler == null) {
            throw new IllegalStateException("Core payload has no server handler: " + type);
        }
        return registration;
    }

    @SuppressWarnings("unchecked")
    private static byte[] encodePayload(CorePayloadRegistration<?> rawRegistration, CorePacketPayload payload, RegistryAccess registryAccess) {
        CorePayloadRegistration<CorePacketPayload> registration = (CorePayloadRegistration<CorePacketPayload>) rawRegistration;
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        try {
            registration.codec.encode(buffer, payload);
            byte[] out = new byte[buffer.readableBytes()];
            buffer.getBytes(0, out);
            return out;
        } finally {
            buffer.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static CorePacketPayload decodePayload(CorePayloadRegistration<?> rawRegistration, byte[] encoded, RegistryAccess registryAccess) {
        CorePayloadRegistration<CorePacketPayload> registration = (CorePayloadRegistration<CorePacketPayload>) rawRegistration;
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(encoded), registryAccess);
        try {
            return registration.codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static RegistryAccess resolveClientRegistryAccess() {
        try {
            Class<?> clazz = Class.forName("link.botwmcs.core.net.client.CoreClientNetworkAccess");
            return (RegistryAccess) clazz.getMethod("registryAccess").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Core client registry access is unavailable on this side.", e);
        }
    }

    private static void openClientNetworkingStatScreen() {
        try {
            Class<?> clazz = Class.forName("link.botwmcs.core.client.debug.CoreClientDebugScreenAccess");
            clazz.getMethod("openNetworkingStatScreen").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("{}Failed to open core networking stat screen on client.", CoreKeys.LOG_PREFIX, e);
        }
    }

    private static final class CorePayloadRegistration<T extends CorePacketPayload> {
        private final CorePayloadType<T> type;
        private final StreamCodec<RegistryFriendlyByteBuf, T> codec;
        private CorePayloadHandler<T> serverHandler;
        private CorePayloadHandler<T> clientHandler;

        private CorePayloadRegistration(CorePayloadType<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
            this.type = type;
            this.codec = codec;
        }
    }
}
