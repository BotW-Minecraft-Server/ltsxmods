package link.botwmcs.ltsxlogica.heat;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.botwmcs.ltsxlogica.data.HeatDataRegistry;
import link.botwmcs.ltsxlogica.data.HeatModelData;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resolves thermal properties from BlockState with a stateId cache.
 */
public final class HeatPropsResolver {
    private final Int2ObjectOpenHashMap<HeatProps> cacheByStateId = new Int2ObjectOpenHashMap<>();
    private int cacheRevision = Integer.MIN_VALUE;

    public HeatProps resolve(BlockState state) {
        ensureCacheRevision();
        final int stateId = Block.getId(state);
        HeatProps cached = this.cacheByStateId.get(stateId);
        if (cached != null) {
            return cached;
        }

        HeatProps computed = compute(state, false);
        this.cacheByStateId.put(stateId, computed);
        return computed;
    }

    public HeatProps resolve(BlockState state, boolean fireOnSoulBase) {
        if (!fireOnSoulBase || !state.is(Blocks.FIRE)) {
            return resolve(state);
        }
        ensureCacheRevision();
        return compute(state, true);
    }

    public void clearCache() {
        this.cacheByStateId.clear();
    }

    public static boolean isSoulFireBase(BlockState state) {
        return state.is(BlockTags.SOUL_FIRE_BASE_BLOCKS) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    private void ensureCacheRevision() {
        int revision = HeatDataRegistry.revision();
        if (this.cacheRevision == revision) {
            return;
        }
        this.cacheRevision = revision;
        this.cacheByStateId.clear();
    }

    private static HeatProps compute(BlockState state, boolean fireOnSoulBase) {
        HeatModelData model = HeatDataRegistry.model();
        if (state.isAir()) {
            return model.airProps();
        }

        HeatModelData.LiquidProfile liquidProfile = model.liquidModel().resolveProfile(state.getFluidState());
        if (liquidProfile != null) {
            return liquidProfile.heatProps();
        }

        for (HeatModelData.HeatPropsRule rule : model.heatPropsRules()) {
            if (rule.matches(state, fireOnSoulBase)) {
                return rule.props();
            }
        }

        return model.defaultProps();
    }
}
