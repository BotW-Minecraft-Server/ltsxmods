package link.botwmcs.ltsxlogica.heat.activities;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.botwmcs.ltsxlogica.data.HeatDataRegistry;
import link.botwmcs.ltsxlogica.data.HeatModelData;
import link.botwmcs.ltsxlogica.heat.HeatBlockTags;
import link.botwmcs.ltsxlogica.heat.HeatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Budgeted queue for temperature-driven block phase changes.
 */
public final class PhaseChangeManager {
    private final LongArrayFIFOQueue pending = new LongArrayFIFOQueue();
    private final LongOpenHashSet dedupe = new LongOpenHashSet();

    public void onTemperatureCrossing(long packedBlockPos, int oldTempFixed, int newTempFixed) {
        HeatModelData.PhaseModel phaseModel = HeatDataRegistry.model().phaseModel();
        if (!phaseModel.crossedAnyThreshold(oldTempFixed, newTempFixed)) {
            return;
        }
        if (this.dedupe.add(packedBlockPos)) {
            this.pending.enqueue(packedBlockPos);
        }
    }

    public void clear() {
        this.pending.clear();
        this.dedupe.clear();
    }

    public void tick(ServerLevel level, HeatManager heatManager, int budget) {
        if (budget <= 0 || this.pending.isEmpty()) {
            return;
        }

        HeatModelData.PhaseModel phaseModel = HeatDataRegistry.model().phaseModel();
        if (phaseModel.rules().isEmpty()) {
            return;
        }

        int processed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        while (processed < budget && !this.pending.isEmpty()) {
            long packed = this.pending.dequeueLong();
            this.dedupe.remove(packed);

            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            cursor.set(x, y, z);

            if (!level.hasChunkAt(cursor)) {
                processed++;
                continue;
            }

            BlockState state = level.getBlockState(cursor);
            if (phaseModel.requirePcmTag() && !state.is(HeatBlockTags.PCM)) {
                processed++;
                continue;
            }

            int tFixed = heatManager.getTemperatureFixed(level, x, y, z);

            for (HeatModelData.PhaseRule rule : phaseModel.rules()) {
                if (!rule.matchesState(state)) {
                    continue;
                }
                if (!rule.meetsThreshold(tFixed)) {
                    continue;
                }
                if (rule.requireSourceFluid() && !state.getFluidState().isSource()) {
                    continue;
                }

                BlockState nextState = rule.resolveResultBlock(level.dimensionType().ultraWarm()).defaultBlockState();
                if (state.is(nextState.getBlock())) {
                    break;
                }

                if (level.setBlock(cursor, nextState, Block.UPDATE_ALL)) {
                    heatManager.onBlockChanged(level, cursor, state, nextState);
                }
                break;
            }

            processed++;
        }
    }
}
