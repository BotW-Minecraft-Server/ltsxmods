package link.botwmcs.ltsxlogica.heat.activities;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.botwmcs.ltsxlogica.data.HeatDataRegistry;
import link.botwmcs.ltsxlogica.data.HeatModelData;
import link.botwmcs.ltsxlogica.heat.HeatBlockTags;
import link.botwmcs.ltsxlogica.heat.HeatManager;
import link.botwmcs.ltsxlogica.heat.HeatPropsResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Budgeted queue for hot-air ignition checks.
 */
public final class IgnitionManager {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final LongArrayFIFOQueue hotAirCandidates = new LongArrayFIFOQueue();
    private final LongOpenHashSet dedupe = new LongOpenHashSet();

    public void onHotAirCandidate(long packedBlockPos) {
        if (this.dedupe.add(packedBlockPos)) {
            this.hotAirCandidates.enqueue(packedBlockPos);
        }
    }

    public void clear() {
        this.hotAirCandidates.clear();
        this.dedupe.clear();
    }

    public void tick(ServerLevel level, HeatManager heatManager, int budget) {
        if (budget <= 0 || this.hotAirCandidates.isEmpty()) {
            return;
        }

        HeatModelData.IgnitionModel ignition = HeatDataRegistry.model().ignitionModel();
        if (ignition.respectFireTickGameRule() && !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return;
        }

        int processed = 0;
        BlockPos.MutableBlockPos airPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

        while (processed < budget && !this.hotAirCandidates.isEmpty()) {
            long packed = this.hotAirCandidates.dequeueLong();
            this.dedupe.remove(packed);

            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            airPos.set(x, y, z);

            if (!level.hasChunkAt(airPos)) {
                processed++;
                continue;
            }

            if (heatManager.getTemperatureFixed(level, x, y, z) < ignition.airThresholdFixed()) {
                processed++;
                continue;
            }

            BlockState airState = level.getBlockState(airPos);
            if (!airState.isAir()) {
                processed++;
                continue;
            }

            boolean hasFuelNeighbor = false;
            for (Direction dir : DIRECTIONS) {
                neighborPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                if (!level.hasChunkAt(neighborPos)) {
                    continue;
                }

                BlockState fuel = level.getBlockState(neighborPos);
                boolean ignitableByTag = ignition.useIgnitableTag() && fuel.is(HeatBlockTags.IGNITABLE);
                boolean ignitableByVanilla = ignition.useVanillaFlammable()
                        && fuel.isFlammable(level, neighborPos, dir.getOpposite());
                if (ignitableByTag || ignitableByVanilla) {
                    hasFuelNeighbor = true;
                    break;
                }
            }

            if (hasFuelNeighbor) {
                BlockState fire = ignition.resultBlock().defaultBlockState();
                if (ignition.preferSoulFireOnSoulBase()
                        && ignition.resultBlock() == Blocks.FIRE
                        && y > level.getMinBuildHeight()) {
                    belowPos.set(x, y - 1, z);
                    if (level.hasChunkAt(belowPos) && HeatPropsResolver.isSoulFireBase(level.getBlockState(belowPos))) {
                        fire = Blocks.SOUL_FIRE.defaultBlockState();
                    }
                }

                if (fire.canSurvive(level, airPos) && level.setBlock(airPos, fire, Block.UPDATE_ALL)) {
                    heatManager.onBlockChanged(level, airPos, airState, fire);
                }
            }

            processed++;
        }
    }
}
