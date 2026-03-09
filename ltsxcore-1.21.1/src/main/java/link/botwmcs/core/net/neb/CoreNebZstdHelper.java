package link.botwmcs.core.net.neb;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zstd context cache for Core NEB transport.
 */
public final class CoreNebZstdHelper {
    private static final Map<Object, CoreNebZstdContext> CONTEXTS = new ConcurrentHashMap<>();

    private CoreNebZstdHelper() {
    }

    public static ByteBuf compress(Object key, ByteBuf raw) {
        if (raw.isDirect()) {
            return Unpooled.wrappedBuffer(get(key).compress(raw.nioBuffer()));
        }

        ByteBuf directBuf = Unpooled.directBuffer(raw.readableBytes());
        raw.getBytes(raw.readerIndex(), directBuf);
        ByteBuf compressed = Unpooled.wrappedBuffer(get(key).compress(directBuf.nioBuffer()));
        directBuf.release();
        return compressed;
    }

    public static ByteBuf decompress(Object key, ByteBuf compressed, int originalSize) {
        if (compressed.isDirect()) {
            return Unpooled.wrappedBuffer(get(key).decompress(compressed.nioBuffer(), originalSize));
        }

        ByteBuf directBuf = Unpooled.directBuffer(compressed.readableBytes());
        compressed.getBytes(compressed.readerIndex(), directBuf);
        ByteBuf decompressed = Unpooled.wrappedBuffer(get(key).decompress(directBuf.nioBuffer(), originalSize));
        directBuf.release();
        return decompressed;
    }

    public static void remove(Object key) {
        CoreNebZstdContext context = CONTEXTS.remove(key);
        if (context != null) {
            context.close();
        }
    }

    private static CoreNebZstdContext get(Object key) {
        return CONTEXTS.computeIfAbsent(key, __ -> new CoreNebZstdContext());
    }
}
