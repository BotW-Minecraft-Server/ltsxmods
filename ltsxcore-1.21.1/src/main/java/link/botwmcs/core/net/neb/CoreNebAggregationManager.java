package link.botwmcs.core.net.neb;

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
import link.botwmcs.core.net.payload.CoreNebBatchPayload;
import link.botwmcs.core.net.payload.CoreNebDirectPayload;
import link.botwmcs.core.net.stat.CoreNebTrafficStat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Core-only aggregation scheduler and transport sender.
 */
public final class CoreNebAggregationManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WeakHashMap<ServerPlayer, ArrayList<CoreNebFrame>> TO_PLAYER_BUFFER = new WeakHashMap<>();
    private static final ArrayList<CoreNebFrame> TO_SERVER_BUFFER = new ArrayList<>();
    private static final Object CLIENT_CONTEXT_KEY = new Object();
    private static final ScheduledExecutorService TIMER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("LTSXCore-NEB-Flush-%d").setDaemon(true).build()
    );
    private static ScheduledFuture<?> flushTask;
    private static volatile boolean initialized = false;

    private CoreNebAggregationManager() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        TO_PLAYER_BUFFER.clear();
        TO_SERVER_BUFFER.clear();

        if (flushTask != null) {
            flushTask.cancel(false);
        }
        flushTask = TIMER.scheduleAtFixedRate(
                CoreNebAggregationManager::flushAll,
                0L,
                CoreConfig.nebFlushPeriodMs(),
                TimeUnit.MILLISECONDS
        );
        initialized = true;
    }

    public static synchronized void queueToPlayer(ServerPlayer player, ResourceLocation type, byte[] data, boolean bypassNeb) {
        if (bypassNeb) {
            flushPlayer(player);
            CoreNebTrafficStat.outRaw(data.length);
            CoreNebTrafficStat.outBaked(data.length);
            PacketDistributor.sendToPlayer(player, new CoreNebDirectPayload(type, data));
            return;
        }
        TO_PLAYER_BUFFER.computeIfAbsent(player, __ -> new ArrayList<>()).add(new CoreNebFrame(type, data));
    }

    public static synchronized void queueToServer(ResourceLocation type, byte[] data, boolean bypassNeb) {
        if (bypassNeb) {
            flushServer();
            CoreNebTrafficStat.outRaw(data.length);
            CoreNebTrafficStat.outBaked(data.length);
            PacketDistributor.sendToServer(new CoreNebDirectPayload(type, data));
            return;
        }
        TO_SERVER_BUFFER.add(new CoreNebFrame(type, data));
    }

    public static synchronized void flushPlayer(ServerPlayer player) {
        ArrayList<CoreNebFrame> frames = TO_PLAYER_BUFFER.get(player);
        if (frames == null || frames.isEmpty()) {
            return;
        }

        try {
            BakedResult baked = bake(frames, player);
            frames.clear();
            CoreNebTrafficStat.outRaw(baked.rawSize());
            CoreNebTrafficStat.outBaked(baked.baked().length);
            PacketDistributor.sendToPlayer(player, new CoreNebBatchPayload(baked.baked()));
        } catch (Exception e) {
            LOGGER.error("Failed to flush core NEB packets to player {}", player.getGameProfile().getName(), e);
        }
    }

    public static synchronized void flushServer() {
        if (TO_SERVER_BUFFER.isEmpty()) {
            return;
        }

        try {
            BakedResult baked = bake(TO_SERVER_BUFFER, CLIENT_CONTEXT_KEY);
            TO_SERVER_BUFFER.clear();
            CoreNebTrafficStat.outRaw(baked.rawSize());
            CoreNebTrafficStat.outBaked(baked.baked().length);
            PacketDistributor.sendToServer(new CoreNebBatchPayload(baked.baked()));
        } catch (Exception e) {
            LOGGER.error("Failed to flush core NEB packets to server", e);
        }
    }

    private static synchronized void flushAll() {
        TO_PLAYER_BUFFER.entrySet().removeIf(entry -> {
            ServerPlayer player = entry.getKey();
            boolean disconnected = player == null || player.connection == null || !player.connection.getConnection().isConnected();
            if (disconnected && player != null) {
                CoreNebZstdHelper.remove(player);
            }
            return disconnected;
        });

        TO_PLAYER_BUFFER.forEach((player, frames) -> {
            if (!frames.isEmpty()) {
                try {
                    BakedResult baked = bake(frames, player);
                    frames.clear();
                    CoreNebTrafficStat.outRaw(baked.rawSize());
                    CoreNebTrafficStat.outBaked(baked.baked().length);
                    PacketDistributor.sendToPlayer(player, new CoreNebBatchPayload(baked.baked()));
                } catch (Exception e) {
                    LOGGER.error("Failed scheduled flush to player {}", player.getGameProfile().getName(), e);
                }
            }
        });

        if (!TO_SERVER_BUFFER.isEmpty()) {
            try {
                BakedResult baked = bake(TO_SERVER_BUFFER, CLIENT_CONTEXT_KEY);
                TO_SERVER_BUFFER.clear();
                CoreNebTrafficStat.outRaw(baked.rawSize());
                CoreNebTrafficStat.outBaked(baked.baked().length);
                PacketDistributor.sendToServer(new CoreNebBatchPayload(baked.baked()));
            } catch (Exception e) {
                LOGGER.error("Failed scheduled flush to server", e);
            }
        }
    }

    private static BakedResult bake(ArrayList<CoreNebFrame> frames, Object zstdKey) {
        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
        for (CoreNebFrame frame : frames) {
            CoreNebPacketPrefixHelper.get().index(frame.type()).save(raw);
            raw.writeVarInt(frame.data().length);
            raw.writeBytes(frame.data());
        }

        int rawSize = raw.readableBytes();
        boolean compress = rawSize >= 32;
        FriendlyByteBuf baked = new FriendlyByteBuf(Unpooled.buffer());
        baked.writeBoolean(compress);

        if (compress) {
            baked.writeVarInt(rawSize);
            ByteBuf compressed = CoreNebZstdHelper.compress(zstdKey, raw);
            baked.writeBytes(compressed);
            if (CoreConfig.nebDebugLog()) {
                LOGGER.debug("Core NEB compressed {} -> {} bytes ({}%)",
                        rawSize,
                        compressed.readableBytes(),
                        String.format("%.2f", 100.0f * compressed.readableBytes() / Math.max(rawSize, 1)));
            }
            compressed.release();
        } else {
            baked.writeBytes(raw);
        }

        byte[] out = new byte[baked.readableBytes()];
        baked.getBytes(0, out);

        raw.release();
        baked.release();
        return new BakedResult(out, rawSize);
    }

    private record BakedResult(byte[] baked, int rawSize) {
    }
}
