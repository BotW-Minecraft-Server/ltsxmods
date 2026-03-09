package link.botwmcs.core.net.client;

import link.botwmcs.core.config.CoreConfig;
import link.botwmcs.core.net.stat.CoreNebPacketFlowStat;
import link.botwmcs.core.net.stat.CoreNebTrafficStat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time traffic view for Core NEB transport.
 */
public final class CoreNetworkStatScreen extends Screen {
    private static final int LINE_HEIGHT = 12;
    private static final int COLOR_E = 0xFFFFFF55;
    private static final int COLOR_7 = 0xFFAAAAAA;
    private static final int COLOR_F = 0xFFFFFFFF;

    private String actualLine = "";
    private String rawLine = "";
    private String ratioLine = "";
    private String modeLine = "";
    private List<CoreNebPacketFlowStat.Row> inboundRows = List.of();
    private List<CoreNebPacketFlowStat.Row> outboundRows = List.of();
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
            modeLine = CoreConfig.nebGlobalMixinEnabled()
                    ? "Mode: Global Connection Mixin (ratio = NEB batch only)"
                    : "Mode: CoreNetwork only";
            if (CoreConfig.nebGlobalMixinEnabled()) {
                modeLine += CoreConfig.nebGlobalFullPacketStat()
                        ? " + full packet stat (NEB + BYPASS)"
                        : " + NEB-only packet stat";
            }
            CoreNebPacketFlowStat.Snapshot flowSnapshot = CoreNebPacketFlowStat.snapshot(12);
            inboundRows = flowSnapshot.inbound();
            outboundRows = flowSnapshot.outbound();
        }
        tick++;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = 10;
        int y = 12;

        guiGraphics.drawString(this.font, "Core Networking Traffic (NEB path)", x, y, COLOR_E, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(this.font, modeLine, x, y, COLOR_7, false);
        y += LINE_HEIGHT * 2;
        guiGraphics.drawString(this.font, "Actual Transmission", x, y, COLOR_7, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(this.font, actualLine, x, y, COLOR_F, false);
        y += LINE_HEIGHT * 2;
        guiGraphics.drawString(this.font, "Raw Payload", x, y, COLOR_7, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(this.font, rawLine, x, y, COLOR_F, false);
        y += LINE_HEIGHT * 2;
        guiGraphics.drawString(this.font, ratioLine, x, y, COLOR_F, false);

        int tableTop = y + LINE_HEIGHT * 2;
        int gap = 12;
        int colWidth = (this.width - x * 2 - gap) / 2;
        int rightX = x + colWidth + gap;

        drawTable(guiGraphics, x, tableTop, colWidth, "Download (Inbound)", inboundRows);
        drawTable(guiGraphics, rightX, tableTop, colWidth, "Upload (Outbound)", outboundRows);
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

    private void drawTable(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            String title,
            List<CoreNebPacketFlowStat.Row> rows
    ) {
        guiGraphics.drawString(this.font, title, x, y, COLOR_E, false);
        y += LINE_HEIGHT;
        guiGraphics.drawString(this.font, "Path | Type | Speed | Total | Count", x, y, COLOR_7, false);
        y += LINE_HEIGHT;

        if (rows.isEmpty()) {
            guiGraphics.drawString(this.font, "<empty>", x, y, COLOR_7, false);
            return;
        }

        int maxLines = Math.max(1, (this.height - y - 8) / LINE_HEIGHT);
        List<RenderLine> lines = renderLines(rows, width);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            RenderLine line = lines.get(i);
            guiGraphics.drawString(this.font, line.text(), x, y + i * LINE_HEIGHT, line.color(), false);
        }
    }

    private List<RenderLine> renderLines(List<CoreNebPacketFlowStat.Row> rows, int width) {
        int maxTextWidth = Math.max(100, width - 8);
        ArrayList<RenderLine> lines = new ArrayList<>(rows.size());
        for (CoreNebPacketFlowStat.Row row : rows) {
            String path = row.path() == CoreNebPacketFlowStat.TrafficPath.NEB ? "NEB" : "BYPASS";
            String suffix = " | " + readableSpeed(row.speedBytesPerSecond())
                    + " | " + readableSize(row.totalBytes())
                    + " | " + row.totalPackets();
            String prefix = path + " | ";
            String type = trimType(row.type(), maxTextWidth - this.font.width(prefix) - this.font.width(suffix));
            int color = row.path() == CoreNebPacketFlowStat.TrafficPath.NEB ? COLOR_E : COLOR_F;
            lines.add(new RenderLine(prefix + type + suffix, color));
        }
        return lines;
    }

    private String trimType(ResourceLocation type, int widthForType) {
        String full = type.toString();
        if (this.font.width(full) <= widthForType) {
            return full;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        if (ellipsisWidth >= widthForType) {
            return ellipsis;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < full.length(); i++) {
            char c = full.charAt(i);
            if (this.font.width(builder.toString() + c) + ellipsisWidth > widthForType) {
                break;
            }
            builder.append(c);
        }
        return builder + ellipsis;
    }

    private record RenderLine(String text, int color) {
    }
}
