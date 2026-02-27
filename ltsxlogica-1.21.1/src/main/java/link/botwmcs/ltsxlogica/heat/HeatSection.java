package link.botwmcs.ltsxlogica.heat;

import java.util.Arrays;
import java.util.BitSet;
import link.botwmcs.ltsxlogica.data.HeatModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-chunk-section thermal storage and sparse stepping state.
 */
public final class HeatSection {
    public static final int EDGE = 16;
    public static final int VOLUME = EDGE * EDGE * EDGE;

    private static final int[] DX = { 1, -1, 0, 0, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DZ = { 0, 0, 0, 0, 1, -1 };

    public short[] temp;
    public short[] tempNext;
    public final BitSet dirty;

    private final int updatePhase;
    private long lastTouchedTick;
    private long lastSignificantChangeTick;

    public HeatSection(int initialAmbientFixed, int updatePhase) {
        this.temp = new short[VOLUME];
        this.tempNext = new short[VOLUME];
        this.dirty = new BitSet(VOLUME);
        this.updatePhase = updatePhase & 7;
        short init = (short) initialAmbientFixed;
        Arrays.fill(this.temp, init);
        Arrays.fill(this.tempNext, init);
        this.lastTouchedTick = 0L;
        this.lastSignificantChangeTick = 0L;
    }

    public static int index(int lx, int ly, int lz) {
        return (ly << 8) | (lz << 4) | lx;
    }

    public int get(int lx, int ly, int lz) {
        return this.temp[index(lx & 15, ly & 15, lz & 15)];
    }

    public void set(int lx, int ly, int lz, int fixedTemp) {
        int idx = index(lx & 15, ly & 15, lz & 15);
        short value = (short) fixedTemp;
        this.temp[idx] = value;
        this.tempNext[idx] = value;
    }

    public void markDirty(int lx, int ly, int lz) {
        this.dirty.set(index(lx & 15, ly & 15, lz & 15));
    }

    public boolean hasDirty() {
        return !this.dirty.isEmpty();
    }

    public long getLastTouchedTick() {
        return this.lastTouchedTick;
    }

    public int step(ServerLevel level, SectionPos secPos, HeatManager ctx, int budgetCells) {
        if (budgetCells <= 0 || this.dirty.isEmpty()) {
            return 0;
        }

        final int baseX = secPos.minBlockX();
        final int baseY = secPos.minBlockY();
        final int baseZ = secPos.minBlockZ();

        int processed = 0;
        int idx = this.dirty.nextSetBit(0);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos indirectNeighborPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

        while (idx >= 0 && processed < budgetCells) {
            this.dirty.clear(idx);

            final int lx = idx & 15;
            final int lz = (idx >>> 4) & 15;
            final int ly = (idx >>> 8) & 15;

            final int x = baseX + lx;
            final int y = baseY + ly;
            final int z = baseZ + lz;
            pos.set(x, y, z);

            if (!level.hasChunkAt(pos)) {
                this.dirty.set(idx);
                idx = this.dirty.nextSetBit(idx + 1);
                continue;
            }

            BlockState state = level.getBlockState(pos);
            boolean fireOnSoulBase = state.is(Blocks.FIRE) && isFireOnSoulBase(level, belowPos, x, y, z);
            HeatProps pi = ctx.getPropsResolver().resolve(state, fireOnSoulBase);
            HeatModelData.LiquidProfile liquidProfile = ctx.resolveLiquidProfile(state);
            boolean liquidCell = liquidProfile != null;

            if (!ctx.shouldUpdateCell(pi, this.updatePhase, idx)) {
                this.dirty.set(idx);
                idx = this.dirty.nextSetBit(idx + 1);
                continue;
            }

            final int tNow = this.temp[idx];
            final int tEnv = ctx.getAmbientFixed(level, x, y, z);

            double condSum = 0.0d;
            int nAirFaces = 0;
            double liquidHeatSource = 0.0d;

            for (int d = 0; d < 6; d++) {
                final int nx = x + DX[d];
                final int ny = y + DY[d];
                final int nz = z + DZ[d];

                neighborPos.set(nx, ny, nz);
                int tj = ctx.getTemperatureFixed(level, nx, ny, nz);

                float kij = pi.conductivityK;
                if (level.hasChunkAt(neighborPos)) {
                    BlockState sj = level.getBlockState(neighborPos);
                    boolean neighborFireOnSoulBase = sj.is(Blocks.FIRE) && isFireOnSoulBase(level, belowPos, nx, ny, nz);
                    HeatProps pj = ctx.getPropsResolver().resolve(sj, neighborFireOnSoulBase);
                    kij = Math.min(pi.conductivityK, pj.conductivityK);
                    if (sj.isAir()) {
                        nAirFaces++;
                    }
                    if (liquidCell) {
                        int direct = ctx.liquidHeatingSourceContributionFixed(liquidProfile, sj);
                        liquidHeatSource += direct;

                        if (direct <= 0 && (sj.isAir() || ctx.isModeledLiquidState(sj))) {
                            int fx = nx + DX[d];
                            int fy = ny + DY[d];
                            int fz = nz + DZ[d];
                            indirectNeighborPos.set(fx, fy, fz);
                            if (level.hasChunkAt(indirectNeighborPos)) {
                                int indirect = ctx.liquidHeatingSourceContributionFixed(
                                        liquidProfile,
                                        level.getBlockState(indirectNeighborPos)
                                );
                                if (indirect > 0) {
                                    liquidHeatSource += indirect * 0.45d;
                                }
                            }
                        }
                    }
                } else {
                    nAirFaces++;
                }

                condSum += kij * (tj - tNow);
            }

            double deltaCond = ctx.dt() * pi.invCapacity * condSum;
            double deltaGen = ctx.dt() * (pi.generationQ * pi.invCapacity);
            double deltaEnv = ctx.dt() * pi.relaxR * (tEnv - tNow);
            double deltaSrc = pi.hasTarget() ? ctx.dt() * pi.sourceStrengthS * (pi.targetFixed - tNow) : 0.0d;
            double deltaConv = ctx.dt() * pi.convectiveH * nAirFaces * (tEnv - tNow);
            double deltaLiquidSrc = 0.0d;
            if (liquidCell && liquidHeatSource > 0.0d) {
                double limited = Math.min(liquidHeatSource, ctx.liquidHeatingSourceClampFixed(liquidProfile));
                deltaLiquidSrc = ctx.dt() * pi.invCapacity * limited;
            }

            int tComputed = ctx.clampFixed((int) Math.round(tNow + deltaCond + deltaGen + deltaEnv + deltaSrc + deltaConv + deltaLiquidSrc));
            int tNext = ctx.mixUnderRelaxation(tNow, tComputed);

            this.tempNext[idx] = (short) tNext;
            this.temp[idx] = (short) tNext; // sparse commit; avoids sweeping full 4096 each tick

            int absDelta = Math.abs(tNext - tNow);
            if (absDelta > ctx.epsilonFixed()) {
                this.lastSignificantChangeTick = ctx.getGameTick();
                ctx.onCellTemperatureChanged(level, x, y, z, tNow, tNext, state);
                ctx.markBlockAndNeighborsDirty(level, x, y, z);
            } else if (pi.isPersistentSource()) {
                // Keep active source cells alive at low cadence.
                this.dirty.set(idx);
            }

            processed++;
            idx = this.dirty.nextSetBit(idx + 1);
        }

        if (processed > 0) {
            this.lastTouchedTick = ctx.getGameTick();
        }
        return processed;
    }

    public void swapBuffers() {
        short[] t = this.temp;
        this.temp = this.tempNext;
        this.tempNext = t;
    }

    public boolean isEvictable(long nowTick, long idleTicks, int ambientFixed, int epsilonFixed) {
        if (!this.dirty.isEmpty()) {
            return false;
        }
        if (nowTick - this.lastSignificantChangeTick < idleTicks) {
            return false;
        }

        // Stride sample: cheap enough for periodic eviction checks.
        for (int i = 0; i < VOLUME; i += 97) {
            if (Math.abs(this.temp[i] - ambientFixed) > (epsilonFixed << 1)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFireOnSoulBase(ServerLevel level, BlockPos.MutableBlockPos belowPos, int x, int y, int z) {
        if (y <= level.getMinBuildHeight()) {
            return false;
        }
        belowPos.set(x, y - 1, z);
        if (!level.hasChunkAt(belowPos)) {
            return false;
        }
        return HeatPropsResolver.isSoulFireBase(level.getBlockState(belowPos));
    }
}
