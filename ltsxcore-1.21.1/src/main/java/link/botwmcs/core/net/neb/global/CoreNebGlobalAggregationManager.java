package link.botwmcs.core.net.neb.global;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.net.neb.CoreNebZstdHelper;
import link.botwmcs.core.net.payload.CoreNebGlobalBatchPayload;
import link.botwmcs.core.net.stat.CoreNebPacketFlowStat;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import org.slf4j.Logger;

/**
 * Global Connection-level aggregation manager used by Connection mixin mode.
 */
public final class CoreNebGlobalAggregationManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WeakHashMap<Connection, ArrayList<Packet<?>>> PACKET_BUFFER = new WeakHashMap<>();
    private static final ScheduledExecutorService TIMER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("LTSXCore-NEB-GlobalFlush-%d").setDaemon(true).build()
    );

    private static volatile boolean initialized = false;
    private static ScheduledFuture<?> flushTask;

    private CoreNebGlobalAggregationManager() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        PACKET_BUFFER.clear();
        if (flushTask != null) {
            flushTask.cancel(false);
        }
        flushTask = TIMER.scheduleAtFixedRate(
                CoreNebGlobalAggregationManager::flushAll,
                0L,
                CoreConfig.nebFlushPeriodMs(),
                TimeUnit.MILLISECONDS
        );
        initialized = true;
    }

    public static synchronized void takeOver(Packet<?> packet, Connection connection) {
        ensureInitialized();
        PACKET_BUFFER.computeIfAbsent(connection, __ -> new ArrayList<>()).add(packet);
    }

    public static synchronized void flushConnection(Connection connection) {
        ensureInitialized();
        ArrayList<Packet<?>> packets = PACKET_BUFFER.get(connection);
        flushInternal(connection, packets);
    }

    public static boolean canUseGlobalBatch(Connection connection) {
        if (connection == null || connection.channel() == null) {
            return false;
        }
        NetworkPayloadSetup setup = ChannelAttributes.getPayloadSetup(connection);
        if (setup == null) {
            return false;
        }
        return setup.getChannel(ConnectionProtocol.PLAY, CoreNebGlobalBatchPayload.TYPE.id()) != null;
    }

    public static boolean canTakeOverPlay(Connection connection) {
        if (!canUseGlobalBatch(connection)) {
            return false;
        }
        try {
            if (connection.getInboundProtocol() == null || connection.getInboundProtocol().id() != ConnectionProtocol.PLAY) {
                return false;
            }
            return resolveOutboundProtocol(connection).id() == ConnectionProtocol.PLAY;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static synchronized void flushAll() {
        PACKET_BUFFER.entrySet().removeIf(entry -> {
            Connection connection = entry.getKey();
            boolean disconnected = connection == null || !connection.isConnected();
            if (disconnected && connection != null) {
                CoreNebZstdHelper.remove(connection);
            }
            return disconnected;
        });

        PACKET_BUFFER.forEach(CoreNebGlobalAggregationManager::flushInternal);
    }

    private static void flushInternal(Connection connection, ArrayList<Packet<?>> packets) {
        if (connection == null || packets == null || packets.isEmpty()) {
            return;
        }
        if (!connection.isConnected()) {
            packets.clear();
            CoreNebZstdHelper.remove(connection);
            return;
        }
        if (!canTakeOverPlay(connection)) {
            for (Packet<?> packet : packets) {
                try {
                    connection.send(packet, null, true);
                } catch (Exception e) {
                    LOGGER.error("Failed to flush bypass packet {} for {}", packet.type(), connection.getRemoteAddress(), e);
                }
            }
            packets.clear();
            return;
        }

        try {
            BakedResult baked = bake(connection, packets);
            packets.clear();
            if (baked == null) {
                return;
            }

            CoreNebGlobalBatchPayload payload = new CoreNebGlobalBatchPayload(baked.baked(), baked.rawSize());
            Packet<?> wrapper = connection.getSending() == PacketFlow.CLIENTBOUND
                    ? new ClientboundCustomPayloadPacket(payload)
                    : new ServerboundCustomPayloadPacket(payload);
            connection.send(wrapper, null, true);
        } catch (Exception e) {
            LOGGER.error("Failed to flush global NEB packets for {}", connection.getRemoteAddress(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static BakedResult bake(Connection connection, ArrayList<Packet<?>> packets) {
        ProtocolInfo<?> protocolInfo = resolveOutboundProtocol(connection);
        StreamCodec<ByteBuf, Packet<?>> codec = (StreamCodec<ByteBuf, Packet<?>>) protocolInfo.codec();

        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
        for (Packet<?> packet : packets) {
            ByteBuf packetData = Unpooled.buffer();
            try {
                codec.encode(packetData, packet);
                CoreNebPacketFlowStat.out(CoreNebGlobalPacketUtil.getTrueType(packet), packetData.readableBytes());
                raw.writeVarInt(packetData.readableBytes());
                raw.writeBytes(packetData, packetData.readerIndex(), packetData.readableBytes());
            } catch (Exception e) {
                LOGGER.error("Skipped packet {} during global NEB aggregation encode", packet.type(), e);
            } finally {
                packetData.release();
            }
        }

        int rawSize = raw.readableBytes();
        if (rawSize <= 0) {
            raw.release();
            return null;
        }

        boolean shouldCompress = rawSize >= 32;
        FriendlyByteBuf baked = new FriendlyByteBuf(Unpooled.buffer());

        if (shouldCompress) {
            try {
                baked.writeBoolean(true);
                baked.writeVarInt(rawSize);
                ByteBuf compressed = CoreNebZstdHelper.compress(connection, raw);
                try {
                    baked.writeBytes(compressed);
                } finally {
                    compressed.release();
                }
            } catch (Exception e) {
                LOGGER.warn("Global NEB compress failed, fallback to raw packet batch for {}", connection.getRemoteAddress(), e);
                baked.clear();
                baked.writeBoolean(false);
                baked.writeBytes(raw, raw.readerIndex(), raw.readableBytes());
            }
        } else {
            baked.writeBoolean(false);
            baked.writeBytes(raw, raw.readerIndex(), raw.readableBytes());
        }

        byte[] out = new byte[baked.readableBytes()];
        baked.getBytes(0, out);

        raw.release();
        baked.release();
        return new BakedResult(out, rawSize);
    }

    private static ProtocolInfo<?> resolveOutboundProtocol(Connection connection) {
        if (connection.channel() == null) {
            throw new IllegalStateException("Connection channel is null.");
        }
        PacketEncoder<?> encoder = connection.channel().pipeline().get(PacketEncoder.class);
        if (encoder == null) {
            throw new IllegalStateException("PacketEncoder not found in connection pipeline.");
        }
        return encoder.getProtocolInfo();
    }

    private static void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }

    private record BakedResult(byte[] baked, int rawSize) {
    }
}
