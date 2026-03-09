package link.botwmcs.core.net.neb;

import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import java.io.Closeable;
import java.nio.ByteBuffer;
import link.botwmcs.core.config.CoreConfig;

/**
 * Per-link zstd state for Core NEB.
 */
public final class CoreNebZstdContext implements Closeable {
    private final ZstdCompressCtx compressCtx;
    private final ZstdDecompressCtx decompressCtx;

    public CoreNebZstdContext() {
        this.compressCtx = new ZstdCompressCtx();
        this.compressCtx.setLevel(3);
        this.compressCtx.setContentSize(false);
        this.compressCtx.setMagicless(true);
        this.compressCtx.setWindowLog(CoreConfig.nebContextLevel());

        this.decompressCtx = new ZstdDecompressCtx();
        this.decompressCtx.setMagicless(true);
    }

    public ByteBuffer compress(ByteBuffer raw) {
        ByteBuffer source = raw.duplicate();
        int maxDstSize = (int) Zstd.compressBound(source.remaining());
        ByteBuffer dst = ByteBuffer.allocateDirect(maxDstSize);
        try {
            compressCtx.compressDirectByteBufferStream(dst, source, EndDirective.FLUSH);
            dst.flip();
            return dst;
        } catch (RuntimeException ignored) {
            compressCtx.reset();
            return compressCtx.compress(raw.duplicate());
        }
    }

    public ByteBuffer decompress(ByteBuffer compressed, int originalSize) {
        ByteBuffer source = compressed.duplicate();
        ByteBuffer dst = ByteBuffer.allocateDirect(originalSize);
        try {
            decompressCtx.decompressDirectByteBufferStream(dst, source);
            dst.flip();
            return dst;
        } catch (RuntimeException ignored) {
            decompressCtx.reset();
            return decompressCtx.decompress(compressed.duplicate(), originalSize);
        }
    }

    @Override
    public void close() {
        compressCtx.close();
        decompressCtx.close();
    }
}
