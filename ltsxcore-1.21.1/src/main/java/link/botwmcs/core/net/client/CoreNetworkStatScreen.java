package link.botwmcs.core.net.client;

import link.botwmcs.core.net.stat.CoreNebTrafficStat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Real-time traffic view for Core NEB transport.
 */
public final class CoreNetworkStatScreen extends Screen {
    private static final int LINE_HEIGHT = 12;

    private String actualLine = "";
    private String rawLine = "";
    private String ratioLine = "";
    private int tick;

    public CoreNetworkStatScreen() {
        super(Component.literal("LTSXCore Networking Debug"));
    }

    @Override
    public void tick() {
        super.tick();
        if (tick % 10 == 0) {
            CoreNebTrafficStat.Snapshot snapshot = CoreNebTrafficStat.snapshot();
            actualLine = "-> Inbound " + readableSpeed(snapshot.inboundSpeedBaked())
                    + "  Total " + readableSize(snapshot.inboundBytesBaked())
                    + "    -> Outbound " + readableSpeed(snapshot.outboundSpeedBaked())
                    + "  Total " + readableSize(snapshot.outboundBytesBaked());
            rawLine = "-> Inbound " + readableSpeed(snapshot.inboundSpeedRaw())
                    + "  Total " + readableSize(snapshot.inboundBytesRaw())
                    + "    -> Outbound " + readableSpeed(snapshot.outboundSpeedRaw())
                    + "  Total " + readableSize(snapshot.outboundBytesRaw());
            ratioLine = "Ratio (actual/raw) inbound "
                    + ratioPercent(snapshot.inboundBytesBaked(), snapshot.inboundBytesRaw())
                    + "   outbound "
                    + ratioPercent(snapshot.outboundBytesBaked(), snapshot.outboundBytesRaw());
        }
        tick++;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = 10;
        int y = 12;
        int color = 0xFFE0E0E0;

        guiGraphics.drawString(this.font, "Core Networking Traffic (NEB path)", x, y, color, false);
        y += LINE_HEIGHT * 2;
        guiGraphics.drawString(this.font, "Actual Transmission", x, y, color, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(this.font, actualLine, x, y, color, false);
        y += LINE_HEIGHT * 2;
        guiGraphics.drawString(this.font, "Raw Payload", x, y, color, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(this.font, rawLine, x, y, color, false);
        y += LINE_HEIGHT * 2;
        guiGraphics.drawString(this.font, ratioLine, x, y, color, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String ratioPercent(long baked, long raw) {
        if (raw <= 0L) {
            return "N/A";
        }
        return String.format("%.2f%%", 100.0d * baked / raw);
    }

    private static String readableSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1_000.0d) {
            return String.format("%.0f Bytes/s", bytesPerSecond);
        }
        if (bytesPerSecond < 1_000_000.0d) {
            return String.format("%.1f KiB/s", bytesPerSecond / 1_024.0d);
        }
        return String.format("%.2f MiB/s", bytesPerSecond / (1_024.0d * 1_024.0d));
    }

    private static String readableSize(long bytes) {
        if (bytes < 1_000L) {
            return bytes + " Bytes";
        }
        if (bytes < 1_000_000L) {
            return String.format("%.1f KiB", bytes / 1_024.0d);
        }
        if (bytes < 1_000_000_000L) {
            return String.format("%.2f MiB", bytes / (1_024.0d * 1_024.0d));
        }
        return String.format("%.2f GiB", bytes / (1_024.0d * 1_024.0d * 1_024.0d));
    }
}
