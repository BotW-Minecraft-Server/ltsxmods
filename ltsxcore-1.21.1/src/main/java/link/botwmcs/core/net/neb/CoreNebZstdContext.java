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
        int maxDstSize = (int) Zstd.compressBound(raw.remaining());
        ByteBuffer dst = ByteBuffer.allocateDirect(maxDstSize);
        compressCtx.compressDirectByteBufferStream(dst, raw, EndDirective.FLUSH);
        dst.flip();
        return dst;
    }

    public ByteBuffer decompress(ByteBuffer compressed, int originalSize) {
        ByteBuffer dst = ByteBuffer.allocateDirect(originalSize);
        decompressCtx.decompressDirectByteBufferStream(dst, compressed);
        dst.flip();
        return dst;
    }

    @Override
    public void close() {
        compressCtx.close();
        decompressCtx.close();
    }
}

