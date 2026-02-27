package link.botwmcs.ltsxlogica.heat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;
import link.botwmcs.ltsxlogica.heat.BiomeAmbientModel;
import link.botwmcs.ltsxlogica.heat.HeatManager;
import link.botwmcs.ltsxlogica.heat.HeatProps;
import link.botwmcs.ltsxlogica.heat.HeatPropsResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class HeatVisionOverlay {
    private static final int RANGE_BLOCKS = 50;
    private static final int RANGE_SQR = RANGE_BLOCKS * RANGE_BLOCKS;
    private static final int GRID_EDGE = RANGE_BLOCKS * 2 + 1;
    private static final int GRID_VOLUME = GRID_EDGE * GRID_EDGE * GRID_EDGE;
    private static final int CENTER_REBUILD_MOVE = 8;
    private static final int SAMPLE_BUDGET_PER_TICK = 5000;
    private static final int NEAR_FULL_RENDER_RADIUS = 20;
    private static final int NEAR_FULL_RENDER_RADIUS_SQR = NEAR_FULL_RENDER_RADIUS * NEAR_FULL_RENDER_RADIUS;
    private static final int MID_RENDER_RADIUS = 34;
    private static final int MID_RENDER_RADIUS_SQR = MID_RENDER_RADIUS * MID_RENDER_RADIUS;
    private static final int FAR_RENDER_RADIUS = 44;
    private static final int FAR_RENDER_RADIUS_SQR = FAR_RENDER_RADIUS * FAR_RENDER_RADIUS;
    private static final int AUTO_RANGE_UPDATE_INTERVAL_TICKS = 4;
    private static final int AUTO_RANGE_TARGET_SAMPLES = 18000;
    private static final int AUTO_RANGE_BINS = 128;
    private static final int AUTO_RANGE_LOW_PERCENT = 2;
    private static final int AUTO_RANGE_MID_PERCENT = 50;
    private static final int AUTO_RANGE_HIGH_PERCENT = 99;
    private static final int AUTO_RANGE_MIN_SPAN_FIXED = HeatManager.toFixed(60.0f);
    private static final int AUTO_RANGE_FALLBACK_COLD_FIXED = HeatManager.toFixed(24.0f);
    private static final int AUTO_RANGE_FALLBACK_HOT_FIXED = HeatManager.toFixed(180.0f);
    private static final int AUTO_RANGE_LOW_MARGIN_FIXED = HeatManager.toFixed(4.0f);
    private static final int AUTO_RANGE_HIGH_MARGIN_FIXED = HeatManager.toFixed(16.0f);
    private static final int AUTO_RANGE_MID_ABOVE_MIN_FIXED = HeatManager.toFixed(16.0f);
    private static final float AUTO_RANGE_MEDIAN_TARGET = 0.24f;
    private static final float AUTO_RANGE_BLEND = 0.18f;
    private static final float BOX_SURFACE_EPSILON = 0.0015f;
    private static final float FILL_ALPHA = 0.24f;
    private static final int SCREEN_TINT_ARGB = 0x4A07132E;

    private static final int PALETTE_COLD = 0x050C2A;
    private static final int PALETTE_COOL = 0x0B3F9C;
    private static final int PALETTE_HOT = 0xD9461E;
    private static final int PALETTE_VERY_HOT = 0xFFB11F;
    private static final int PALETTE_BLAZE = 0xFFE089;
    private static final int PALETTE_WHITE = 0xFFFFFF;

    private static final int[] DX = { 1, -1, 0, 0, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DZ = { 0, 0, 0, 0, 1, -1 };

    private static final SphereOffsets SPHERE = buildSphereOffsets();

    private final HeatPropsResolver propsResolver = new HeatPropsResolver();
    private final BlockPos.MutableBlockPos probePos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();
    private final int[] gridTempFixed = new int[GRID_VOLUME];
    private final int[] autoRangeHistogram = new int[AUTO_RANGE_BINS];
    private final Long2IntOpenHashMap biomeAmbientByQuart = new Long2IntOpenHashMap();

    private boolean enabled = false;
    private boolean gridInitialized = false;
    private int centerX = Integer.MIN_VALUE;
    private int centerY = Integer.MIN_VALUE;
    private int centerZ = Integer.MIN_VALUE;
    private int sampleCursor = 0;
    private int biomeAmbientCacheTick = Integer.MIN_VALUE;
    private int statsTickCounter = 0;
    private int autoMinFixed = HeatManager.toFixed(5.0f);
    private int autoMaxFixed = HeatManager.toFixed(120.0f);

    HeatVisionOverlay() {
        this.biomeAmbientByQuart.defaultReturnValue(Integer.MIN_VALUE);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.gridInitialized = false;
            this.biomeAmbientByQuart.clear();
        }
    }

    boolean isEnabled() {
        return this.enabled;
    }

    boolean toggleEnabled() {
        setEnabled(!this.enabled);
        return this.enabled;
    }

    void renderScreenTint(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.enabled || mc.player == null || mc.level == null) {
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        guiGraphics.fill(0, 0, w, h, SCREEN_TINT_ARGB);
    }

    void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!this.enabled || mc.player == null || mc.level == null) {
            return;
        }

        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();
        if (this.biomeAmbientCacheTick != mc.player.tickCount) {
            this.biomeAmbientCacheTick = mc.player.tickCount;
            this.biomeAmbientByQuart.clear();
        }

        if (!this.gridInitialized || movedTooFar(px, py, pz)) {
            resetGrid(mc.level, px, py, pz);
        }

        for (int i = 0; i < SAMPLE_BUDGET_PER_TICK; i++) {
            sampleOneCell(mc.level);
        }

        this.statsTickCounter++;
        if ((this.statsTickCounter % AUTO_RANGE_UPDATE_INTERVAL_TICKS) == 0) {
            updateAutoRange(mc.level, px, py, pz);
        }
    }

    void render(RenderLevelStageEvent event) {
        if (!this.enabled || !this.gridInitialized) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        var cameraPos = event.getCamera().getPosition();
        int visMin = this.autoMinFixed;
        int visMax = this.autoMaxFixed;
        if (visMax <= visMin) {
            int ambient = ambientFixed(mc.level, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
            visMin = ambient - AUTO_RANGE_FALLBACK_COLD_FIXED;
            visMax = ambient + AUTO_RANGE_FALLBACK_HOT_FIXED;
        }

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer fill = buffer.getBuffer(RenderType.debugFilledBox());

        for (int i = 0; i < SPHERE.size; i++) {
            int x = this.centerX + SPHERE.dx[i];
            int y = this.centerY + SPHERE.dy[i];
            int z = this.centerZ + SPHERE.dz[i];
            if (!shouldRenderCell(x, y, z)) {
                continue;
            }
            if (y < mc.level.getMinBuildHeight() || y >= mc.level.getMaxBuildHeight()) {
                continue;
            }
            this.probePos.set(x, y, z);
            if (!mc.level.hasChunkAt(this.probePos)) {
                continue;
            }

            int localIndex = SPHERE.localIndex[i];
            int color = rgbForTemperature(smoothedTempFixed(SPHERE.lx[i], SPHERE.ly[i], SPHERE.lz[i], localIndex), visMin, visMax);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            double minX = x - BOX_SURFACE_EPSILON - cameraPos.x;
            double minY = y - BOX_SURFACE_EPSILON - cameraPos.y;
            double minZ = z - BOX_SURFACE_EPSILON - cameraPos.z;
            double maxX = x + 1.0 + BOX_SURFACE_EPSILON - cameraPos.x;
            double maxY = y + 1.0 + BOX_SURFACE_EPSILON - cameraPos.y;
            double maxZ = z + 1.0 + BOX_SURFACE_EPSILON - cameraPos.z;

            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fill,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    r,
                    g,
                    b,
                    FILL_ALPHA
            );
        }
        buffer.endBatch(RenderType.debugFilledBox());
    }

    private boolean movedTooFar(int px, int py, int pz) {
        return Math.abs(px - this.centerX) >= CENTER_REBUILD_MOVE
                || Math.abs(py - this.centerY) >= CENTER_REBUILD_MOVE
                || Math.abs(pz - this.centerZ) >= CENTER_REBUILD_MOVE;
    }

    private void resetGrid(ClientLevel level, int px, int py, int pz) {
        this.centerX = px;
        this.centerY = py;
        this.centerZ = pz;
        this.sampleCursor = 0;
        this.statsTickCounter = 0;
        this.gridInitialized = true;

        int centerAmbient = ambientFixed(level, px, py, pz);
        Arrays.fill(this.gridTempFixed, centerAmbient);
        for (int i = 0; i < SPHERE.size; i++) {
            int worldX = this.centerX + SPHERE.dx[i];
            int worldY = this.centerY + SPHERE.dy[i];
            int worldZ = this.centerZ + SPHERE.dz[i];
            this.gridTempFixed[SPHERE.localIndex[i]] = ambientFixed(level, worldX, worldY, worldZ);
        }
        this.autoMinFixed = centerAmbient - AUTO_RANGE_FALLBACK_COLD_FIXED;
        this.autoMaxFixed = centerAmbient + AUTO_RANGE_FALLBACK_HOT_FIXED;
    }

    private void sampleOneCell(ClientLevel level) {
        int sphereIndex = this.sampleCursor++;
        if (this.sampleCursor >= SPHERE.size) {
            this.sampleCursor = 0;
        }

        int lx = SPHERE.lx[sphereIndex];
        int ly = SPHERE.ly[sphereIndex];
        int lz = SPHERE.lz[sphereIndex];
        int localIndex = SPHERE.localIndex[sphereIndex];

        int x = this.centerX + SPHERE.dx[sphereIndex];
        int y = this.centerY + SPHERE.dy[sphereIndex];
        int z = this.centerZ + SPHERE.dz[sphereIndex];

        int ambient = ambientFixed(level, x, y, z);
        int oldTemp = this.gridTempFixed[localIndex];

        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            this.gridTempFixed[localIndex] = ambient;
            return;
        }

        this.probePos.set(x, y, z);
        if (!level.hasChunkAt(this.probePos)) {
            this.gridTempFixed[localIndex] = ambient;
            return;
        }

        BlockState state = level.getBlockState(this.probePos);
        HeatProps props = resolveProps(level, x, y, z, state);
        int nextTemp = estimateTemperatureFixed(lx, ly, lz, oldTemp, ambient, props);
        this.gridTempFixed[localIndex] = nextTemp;
    }

    private int estimateTemperatureFixed(
            int lx,
            int ly,
            int lz,
            int oldTemp,
            int ambient,
            HeatProps props
    ) {
        if (props.hasTarget()) {
            float sourcePull = Mth.clamp(props.sourceStrengthS * 0.9f + 0.25f, 0.25f, 1.0f);
            int next = oldTemp + Math.round((props.targetFixed - oldTemp) * sourcePull);
            return Mth.clamp(next, HeatManager.MIN_TEMP_FIXED, HeatManager.MAX_TEMP_FIXED);
        }

        int neighborSum = 0;
        int neighborCount = 0;
        for (int d = 0; d < 6; d++) {
            int nlx = lx + DX[d];
            int nly = ly + DY[d];
            int nlz = lz + DZ[d];
            if (nlx < 0 || nlx >= GRID_EDGE || nly < 0 || nly >= GRID_EDGE || nlz < 0 || nlz >= GRID_EDGE) {
                neighborSum += ambient;
                neighborCount++;
                continue;
            }
            neighborSum += this.gridTempFixed[indexLocal(nlx, nly, nlz)];
            neighborCount++;
        }
        int neighborAvg = neighborCount == 0 ? ambient : Math.round(neighborSum / (float) neighborCount);

        float conductiveFactor = Mth.clamp(
                props.conductivityK * props.invCapacity * 1.05f,
                props.airLike ? 0.08f : 0.02f,
                0.80f
        );
        float relaxFactor = Mth.clamp(props.relaxR * 1.8f, 0.0f, props.airLike ? 0.08f : 0.20f);
        int generationPart = Math.round(props.generationQ * props.invCapacity);

        int next = oldTemp
                + Math.round((neighborAvg - oldTemp) * conductiveFactor)
                + Math.round((ambient - oldTemp) * relaxFactor)
                + generationPart;
        if (props.airLike && neighborAvg > ambient) {
            next += Math.round((neighborAvg - ambient) * 0.10f);
        }
        return Mth.clamp(next, HeatManager.MIN_TEMP_FIXED, HeatManager.MAX_TEMP_FIXED);
    }

    private HeatProps resolveProps(ClientLevel level, int x, int y, int z, BlockState state) {
        if (!state.is(Blocks.FIRE)) {
            return this.propsResolver.resolve(state);
        }
        boolean soulBase = false;
        if (y > level.getMinBuildHeight()) {
            this.belowPos.set(x, y - 1, z);
            if (level.hasChunkAt(this.belowPos)) {
                soulBase = HeatPropsResolver.isSoulFireBase(level.getBlockState(this.belowPos));
            }
        }
        return this.propsResolver.resolve(state, soulBase);
    }

    private int ambientFixed(ClientLevel level, int x, int y, int z) {
        int sampleY = Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        int biomeBase = blendedBiomeBaseFixed(level, x, sampleY, z);

        int gradient = (level.getSeaLevel() - y) >> 3;
        int ambient = biomeBase + gradient;
        return Mth.clamp(ambient, HeatManager.MIN_TEMP_FIXED, HeatManager.MAX_TEMP_FIXED);
    }

    private int blendedBiomeBaseFixed(ClientLevel level, int x, int y, int z) {
        int shiftedX = x - 2;
        int shiftedY = y - 2;
        int shiftedZ = z - 2;

        int qx0 = Math.floorDiv(shiftedX, 4);
        int qy0 = Math.floorDiv(shiftedY, 4);
        int qz0 = Math.floorDiv(shiftedZ, 4);
        int qx1 = qx0 + 1;
        int qy1 = qy0 + 1;
        int qz1 = qz0 + 1;

        int fx = Math.floorMod(shiftedX, 4);
        int fy = Math.floorMod(shiftedY, 4);
        int fz = Math.floorMod(shiftedZ, 4);
        int wx0 = 4 - fx;
        int wy0 = 4 - fy;
        int wz0 = 4 - fz;
        int wx1 = fx;
        int wy1 = fy;
        int wz1 = fz;

        long sum = 0L;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy0, qz0) * wx0 * wy0 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy0, qz0) * wx1 * wy0 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy1, qz0) * wx0 * wy1 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy1, qz0) * wx1 * wy1 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy0, qz1) * wx0 * wy0 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy0, qz1) * wx1 * wy0 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy1, qz1) * wx0 * wy1 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy1, qz1) * wx1 * wy1 * wz1;
        return (int) Math.round(sum / 64.0d);
    }

    private int sampleBiomeBaseFixedAtQuart(ClientLevel level, int quartX, int quartY, int quartZ) {
        int sampleX = QuartPos.toBlock(quartX) + 2;
        int sampleY = QuartPos.toBlock(quartY) + 2;
        int sampleZ = QuartPos.toBlock(quartZ) + 2;
        int clampedY = Mth.clamp(sampleY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        long cacheKey = BlockPos.asLong(sampleX, clampedY, sampleZ);

        int cached = this.biomeAmbientByQuart.get(cacheKey);
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }

        this.probePos.set(sampleX, clampedY, sampleZ);
        int resolved = BiomeAmbientModel.resolveAmbientFixed(level, level.getBiome(this.probePos));
        this.biomeAmbientByQuart.put(cacheKey, resolved);
        return resolved;
    }

    private static int rgbForTemperature(int fixedTemp, int visMinFixed, int visMaxFixed) {
        if (visMaxFixed <= visMinFixed) {
            return PALETTE_WHITE;
        }
        float t = (fixedTemp - visMinFixed) / (float) (visMaxFixed - visMinFixed);
        t = Mth.clamp(t, 0.0f, 1.0f);
        t = (t * 0.55f) + (t * t * 0.45f);
        return thermalPaletteRgb(t);
    }

    private static int thermalPaletteRgb(float t) {
        if (t <= 0.35f) {
            return lerpRgb(PALETTE_COLD, PALETTE_COOL, t / 0.35f);
        }
        if (t <= 0.60f) {
            return lerpRgb(PALETTE_COOL, PALETTE_HOT, (t - 0.35f) / 0.25f);
        }
        if (t <= 0.80f) {
            return lerpRgb(PALETTE_HOT, PALETTE_VERY_HOT, (t - 0.60f) / 0.20f);
        }
        if (t <= 0.94f) {
            return lerpRgb(PALETTE_VERY_HOT, PALETTE_BLAZE, (t - 0.80f) / 0.14f);
        }
        return lerpRgb(PALETTE_BLAZE, PALETTE_WHITE, (t - 0.94f) / 0.06f);
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = ar + Math.round((br - ar) * t);
        int g = ag + Math.round((bg - ag) * t);
        int blue = ab + Math.round((bb - ab) * t);
        return (r << 16) | (g << 8) | blue;
    }

    private static int indexLocal(int lx, int ly, int lz) {
        return (ly * GRID_EDGE + lz) * GRID_EDGE + lx;
    }

    private int smoothedTempFixed(int lx, int ly, int lz, int localIndex) {
        int center = this.gridTempFixed[localIndex];
        int sum = center * 8;
        int weight = 8;
        for (int ox = -1; ox <= 1; ox++) {
            int nx = lx + ox;
            for (int oy = -1; oy <= 1; oy++) {
                int ny = ly + oy;
                for (int oz = -1; oz <= 1; oz++) {
                    int nz = lz + oz;
                    if (ox == 0 && oy == 0 && oz == 0) {
                        continue;
                    }
                    int manhattan = Math.abs(ox) + Math.abs(oy) + Math.abs(oz);
                    int w = switch (manhattan) {
                        case 1 -> 4;
                        case 2 -> 2;
                        default -> 1;
                    };
                    int sample = center;
                    if (nx >= 0 && nx < GRID_EDGE && ny >= 0 && ny < GRID_EDGE && nz >= 0 && nz < GRID_EDGE) {
                        sample = this.gridTempFixed[indexLocal(nx, ny, nz)];
                    }
                    sum += sample * w;
                    weight += w;
                }
            }
        }
        return Math.round(sum / (float) weight);
    }

    private void updateAutoRange(ClientLevel level, int fallbackX, int fallbackY, int fallbackZ) {
        Arrays.fill(this.autoRangeHistogram, 0);
        int stride = Math.max(1, SPHERE.size / AUTO_RANGE_TARGET_SAMPLES);
        int samples = 0;

        for (int i = 0; i < SPHERE.size; i += stride) {
            int x = this.centerX + SPHERE.dx[i];
            int y = this.centerY + SPHERE.dy[i];
            int z = this.centerZ + SPHERE.dz[i];
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }
            this.probePos.set(x, y, z);
            if (!level.hasChunkAt(this.probePos)) {
                continue;
            }

            int temp = this.gridTempFixed[SPHERE.localIndex[i]];
            int bin = toRangeBin(temp);
            this.autoRangeHistogram[bin]++;
            samples++;
        }

        if (samples < 256) {
            int ambient = ambientFixed(level, fallbackX, fallbackY, fallbackZ);
            blendAutoRange(
                    ambient - AUTO_RANGE_FALLBACK_COLD_FIXED,
                    ambient + AUTO_RANGE_FALLBACK_HOT_FIXED
            );
            return;
        }

        int lowRank = Math.max(1, (samples * AUTO_RANGE_LOW_PERCENT) / 100);
        int midRank = Math.max(lowRank + 1, (samples * AUTO_RANGE_MID_PERCENT) / 100);
        int highRank = Math.max(midRank + 1, (samples * AUTO_RANGE_HIGH_PERCENT) / 100);
        int lowBin = quantileBin(lowRank);
        int midBin = quantileBin(midRank);
        int highBin = quantileBin(highRank);

        int low = fromRangeBin(lowBin) - AUTO_RANGE_LOW_MARGIN_FIXED;
        int mid = fromRangeBin(midBin);
        int high = fromRangeBin(highBin) + AUTO_RANGE_HIGH_MARGIN_FIXED;
        high = Math.max(high, mid + AUTO_RANGE_MID_ABOVE_MIN_FIXED);
        if (high - low < AUTO_RANGE_MIN_SPAN_FIXED) {
            int center = (low + high) >> 1;
            low = center - (AUTO_RANGE_MIN_SPAN_FIXED >> 1);
            high = low + AUTO_RANGE_MIN_SPAN_FIXED;
        }

        if (mid > low) {
            int currentSpan = Math.max(1, high - low);
            float midT = (mid - low) / (float) currentSpan;
            if (midT > AUTO_RANGE_MEDIAN_TARGET) {
                int targetSpan = Math.round((mid - low) / AUTO_RANGE_MEDIAN_TARGET);
                high = Math.max(high, low + targetSpan);
            }
        }

        low = Mth.clamp(low, HeatManager.MIN_TEMP_FIXED, HeatManager.MAX_TEMP_FIXED - 1);
        high = Mth.clamp(high, low + 1, HeatManager.MAX_TEMP_FIXED);
        blendAutoRange(low, high);
    }

    private void blendAutoRange(int low, int high) {
        this.autoMinFixed = Math.round(Mth.lerp(AUTO_RANGE_BLEND, this.autoMinFixed, low));
        this.autoMaxFixed = Math.round(Mth.lerp(AUTO_RANGE_BLEND, this.autoMaxFixed, high));
        if (this.autoMaxFixed <= this.autoMinFixed) {
            this.autoMaxFixed = this.autoMinFixed + 1;
        }
    }

    private int quantileBin(int rank) {
        int acc = 0;
        for (int i = 0; i < AUTO_RANGE_BINS; i++) {
            acc += this.autoRangeHistogram[i];
            if (acc >= rank) {
                return i;
            }
        }
        return AUTO_RANGE_BINS - 1;
    }

    private static int toRangeBin(int temp) {
        long clamped = Mth.clamp(temp, HeatManager.MIN_TEMP_FIXED, HeatManager.MAX_TEMP_FIXED);
        long numerator = (clamped - HeatManager.MIN_TEMP_FIXED) * AUTO_RANGE_BINS;
        long denominator = (long) HeatManager.MAX_TEMP_FIXED - HeatManager.MIN_TEMP_FIXED + 1L;
        int bin = (int) (numerator / denominator);
        return Mth.clamp(bin, 0, AUTO_RANGE_BINS - 1);
    }

    private static int fromRangeBin(int bin) {
        int clampedBin = Mth.clamp(bin, 0, AUTO_RANGE_BINS - 1);
        float t = (clampedBin + 0.5f) / AUTO_RANGE_BINS;
        return HeatManager.MIN_TEMP_FIXED + Math.round((HeatManager.MAX_TEMP_FIXED - HeatManager.MIN_TEMP_FIXED) * t);
    }

    private boolean shouldRenderCell(int x, int y, int z) {
        int dx = x - this.centerX;
        int dy = y - this.centerY;
        int dz = z - this.centerZ;
        int dist2 = dx * dx + dy * dy + dz * dz;
        if (dist2 <= NEAR_FULL_RENDER_RADIUS_SQR) {
            return true;
        }
        if (dist2 <= MID_RENDER_RADIUS_SQR) {
            return ((x + y + z) & 1) == 0;
        }
        if (dist2 <= FAR_RENDER_RADIUS_SQR) {
            return ((((x >> 1) + (y >> 1) + (z >> 1)) & 1) == 0);
        }
        return ((x & 1) == 0) && ((y & 1) == 0) && ((z & 1) == 0);
    }

    private static SphereOffsets buildSphereOffsets() {
        int count = 0;
        for (int dx = -RANGE_BLOCKS; dx <= RANGE_BLOCKS; dx++) {
            int dx2 = dx * dx;
            for (int dy = -RANGE_BLOCKS; dy <= RANGE_BLOCKS; dy++) {
                int dxy2 = dx2 + (dy * dy);
                if (dxy2 > RANGE_SQR) {
                    continue;
                }
                for (int dz = -RANGE_BLOCKS; dz <= RANGE_BLOCKS; dz++) {
                    if (dxy2 + (dz * dz) <= RANGE_SQR) {
                        count++;
                    }
                }
            }
        }

        int[] dxArray = new int[count];
        int[] dyArray = new int[count];
        int[] dzArray = new int[count];
        int[] lxArray = new int[count];
        int[] lyArray = new int[count];
        int[] lzArray = new int[count];
        int[] localIdxArray = new int[count];

        int cursor = 0;
        for (int dx = -RANGE_BLOCKS; dx <= RANGE_BLOCKS; dx++) {
            int dx2 = dx * dx;
            int lx = dx + RANGE_BLOCKS;
            for (int dy = -RANGE_BLOCKS; dy <= RANGE_BLOCKS; dy++) {
                int dxy2 = dx2 + (dy * dy);
                if (dxy2 > RANGE_SQR) {
                    continue;
                }
                int ly = dy + RANGE_BLOCKS;
                for (int dz = -RANGE_BLOCKS; dz <= RANGE_BLOCKS; dz++) {
                    if (dxy2 + (dz * dz) > RANGE_SQR) {
                        continue;
                    }

                    int lz = dz + RANGE_BLOCKS;
                    dxArray[cursor] = dx;
                    dyArray[cursor] = dy;
                    dzArray[cursor] = dz;
                    lxArray[cursor] = lx;
                    lyArray[cursor] = ly;
                    lzArray[cursor] = lz;
                    localIdxArray[cursor] = indexLocal(lx, ly, lz);
                    cursor++;
                }
            }
        }

        return new SphereOffsets(count, dxArray, dyArray, dzArray, lxArray, lyArray, lzArray, localIdxArray);
    }

    private record SphereOffsets(
            int size,
            int[] dx,
            int[] dy,
            int[] dz,
            int[] lx,
            int[] ly,
            int[] lz,
            int[] localIndex
    ) {
    }
}
