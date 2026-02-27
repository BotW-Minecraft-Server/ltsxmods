package link.botwmcs.ltsxlogica.data;

import java.util.ArrayList;
import java.util.List;
import link.botwmcs.ltsxlogica.heat.HeatBlockTags;
import link.botwmcs.ltsxlogica.heat.HeatManager;
import link.botwmcs.ltsxlogica.heat.HeatProps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * In-memory heat model snapshot loaded from datapacks.
 */
public final class HeatModelData {
    private final HeatProps airProps;
    private final HeatProps defaultProps;
    private final List<HeatPropsRule> heatPropsRules;
    private final LiquidModel liquidModel;
    private final BiomeModel biomeModel;
    private final IgnitionModel ignitionModel;
    private final PhaseModel phaseModel;

    public HeatModelData(
            HeatProps airProps,
            HeatProps defaultProps,
            List<HeatPropsRule> heatPropsRules,
            LiquidModel liquidModel,
            BiomeModel biomeModel,
            IgnitionModel ignitionModel,
            PhaseModel phaseModel
    ) {
        this.airProps = airProps;
        this.defaultProps = defaultProps;
        this.heatPropsRules = List.copyOf(heatPropsRules);
        this.liquidModel = liquidModel;
        this.biomeModel = biomeModel;
        this.ignitionModel = ignitionModel;
        this.phaseModel = phaseModel;
    }

    public static HeatModelData defaults() {
        HeatProps fire = HeatProps.of(0.12f, 1.2f, 0.0f, 0.010f, 900.0f, 0.25f, 0.10f, 1, true);
        HeatProps soulFire = HeatProps.of(0.10f, 1.4f, 0.0f, 0.010f, 650.0f, 0.20f, 0.08f, 1, true);
        HeatProps campfireLit = HeatProps.of(0.35f, 8.0f, 0.0f, 0.006f, 520.0f, 0.10f, 0.05f, 1, false);
        HeatProps soulCampfireLit = HeatProps.of(0.30f, 8.5f, 0.0f, 0.006f, 360.0f, 0.09f, 0.04f, 1, false);
        HeatProps campfireUnlit = HeatProps.ofNoTarget(0.90f, 16.0f, 0.0f, 0.003f, 0.01f, 4, false);
        HeatProps torch = HeatProps.of(0.08f, 2.0f, 0.0f, 0.012f, 260.0f, 0.10f, 0.05f, 2, true);
        HeatProps redstoneTorchLit = HeatProps.of(0.07f, 2.2f, 0.0f, 0.012f, 180.0f, 0.08f, 0.04f, 2, true);
        HeatProps redstoneTorchUnlit = HeatProps.ofNoTarget(0.20f, 6.0f, 0.0f, 0.004f, 0.01f, 4, false);
        HeatProps furnaceLike = HeatProps.of(1.30f, 16.0f, 64.0f, 0.004f, 380.0f, 0.08f, 0.02f, 2, false);
        HeatProps ice = HeatProps.of(1.90f, 10.0f, 0.0f, 0.004f, -5.0f, 0.05f, 0.02f, 3, false);
        HeatProps wood = HeatProps.ofNoTarget(0.25f, 11.0f, 0.0f, 0.003f, 0.01f, 4, false);
        HeatProps leaves = HeatProps.ofNoTarget(0.16f, 4.0f, 0.0f, 0.008f, 0.02f, 2, false);
        // Real-world-ish stone baseline: higher conductivity than wood, moderate heat capacity, low convective coupling.
        HeatProps stone = HeatProps.ofNoTarget(2.20f, 20.0f, 0.0f, 0.0015f, 0.00f, 4, false);
        HeatProps stoneDefault = HeatProps.ofNoTarget(1.10f, 14.0f, 0.0f, 0.002f, 0.00f, 4, false);

        List<HeatPropsRule> propsRules = new ArrayList<>();
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.FIRE, null, null, Boolean.TRUE), soulFire));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.SOUL_FIRE, null, null, null), soulFire));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.FIRE, null, null, null), fire));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.MAGMA_BLOCK, null, null, null), HeatProps.of(
                1.60f,
                18.0f,
                0.0f,
                0.002f,
                1300.0f,
                0.18f,
                0.04f,
                1,
                false
        )));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.SOUL_CAMPFIRE, null, Boolean.TRUE, null), soulCampfireLit));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.CAMPFIRE, null, Boolean.TRUE, null), campfireLit));
        propsRules.add(new HeatPropsRule(new BlockMatcher(null, blockTag("minecraft:campfires"), Boolean.FALSE, null), campfireUnlit));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.REDSTONE_TORCH, null, Boolean.TRUE, null), redstoneTorchLit));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.REDSTONE_WALL_TORCH, null, Boolean.TRUE, null), redstoneTorchLit));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.REDSTONE_TORCH, null, Boolean.FALSE, null), redstoneTorchUnlit));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.REDSTONE_WALL_TORCH, null, Boolean.FALSE, null), redstoneTorchUnlit));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.TORCH, null, null, null), torch));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.WALL_TORCH, null, null, null), torch));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.FURNACE, null, Boolean.TRUE, null), furnaceLike));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.BLAST_FURNACE, null, Boolean.TRUE, null), furnaceLike));
        propsRules.add(new HeatPropsRule(new BlockMatcher(Blocks.SMOKER, null, Boolean.TRUE, null), furnaceLike));
        propsRules.add(new HeatPropsRule(new BlockMatcher(null, HeatBlockTags.THERMAL_ICE, null, null), ice));
        propsRules.add(new HeatPropsRule(new BlockMatcher(null, HeatBlockTags.THERMAL_STONE, null, null), stone));
        propsRules.add(new HeatPropsRule(new BlockMatcher(null, HeatBlockTags.THERMAL_WOOD, null, null), wood));
        propsRules.add(new HeatPropsRule(new BlockMatcher(null, HeatBlockTags.THERMAL_LEAVES, null, null), leaves));

        List<LiquidSourceRule> defaultLiquidSources = defaultLiquidSourceRules();
        LiquidProfile defaultLiquid = new LiquidProfile(
                HeatProps.ofNoTarget(0.90f, 42.0f, 0.0f, 0.0035f, 0.10f, 2, false),
                24,
                200,
                20 * 90,
                HeatManager.toFixed(-3.2f),
                HeatManager.toFixed(6.0f),
                HeatManager.toFixed(0.18f),
                HeatManager.toFixed(16.0f),
                HeatManager.toFixed(18.0f),
                HeatManager.toFixed(-24.0f),
                HeatManager.toFixed(22.0f),
                0.012f,
                0.020f,
                defaultLiquidSources
        );

        LiquidProfile lavaLiquid = new LiquidProfile(
                HeatProps.of(1.60f, 18.0f, 0.0f, 0.002f, 1300.0f, 0.18f, 0.04f, 1, false),
                20,
                200,
                20 * 90,
                HeatManager.toFixed(26.0f),
                HeatManager.toFixed(2.0f),
                HeatManager.toFixed(0.04f),
                HeatManager.toFixed(5.0f),
                HeatManager.toFixed(24.0f),
                HeatManager.toFixed(8.0f),
                HeatManager.toFixed(120.0f),
                0.060f,
                0.015f,
                defaultLiquidSources
        );

        List<LiquidEntry> liquidEntries = new ArrayList<>();
        liquidEntries.add(new LiquidEntry(
                new FluidMatcher(null, fluidTag("minecraft:lava")),
                lavaLiquid
        ));

        LiquidModel liquidModel = new LiquidModel(defaultLiquid, liquidEntries);

        BiomeModel biomeModel = defaultBiomeModel();

        IgnitionModel ignitionModel = new IgnitionModel(
                HeatManager.toFixed(280.0f),
                true,
                true,
                true,
                Blocks.FIRE,
                true
        );

        List<PhaseRule> phaseRules = new ArrayList<>();
        phaseRules.add(new PhaseRule(
                new BlockMatcher(Blocks.ICE, null, null, null),
                ThresholdDirection.ABOVE,
                HeatManager.toFixed(1.0f),
                Blocks.WATER,
                Blocks.AIR,
                false
        ));
        phaseRules.add(new PhaseRule(
                new BlockMatcher(Blocks.WATER, null, null, null),
                ThresholdDirection.BELOW,
                HeatManager.toFixed(-1.0f),
                Blocks.ICE,
                null,
                true
        ));

        PhaseModel phaseModel = new PhaseModel(true, phaseRules);
        return new HeatModelData(HeatProps.AIR, stoneDefault, propsRules, liquidModel, biomeModel, ignitionModel, phaseModel);
    }

    private static List<LiquidSourceRule> defaultLiquidSourceRules() {
        List<LiquidSourceRule> rules = new ArrayList<>();
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.LAVA, null, null, null), HeatManager.toFixed(12.0f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.MAGMA_BLOCK, null, null, null), HeatManager.toFixed(8.0f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.FIRE, null, null, null), HeatManager.toFixed(6.0f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.SOUL_FIRE, null, null, null), HeatManager.toFixed(4.5f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.CAMPFIRE, null, Boolean.TRUE, null), HeatManager.toFixed(5.0f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.SOUL_CAMPFIRE, null, Boolean.TRUE, null), HeatManager.toFixed(4.0f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.FURNACE, null, Boolean.TRUE, null), HeatManager.toFixed(3.5f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.BLAST_FURNACE, null, Boolean.TRUE, null), HeatManager.toFixed(3.5f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.SMOKER, null, Boolean.TRUE, null), HeatManager.toFixed(3.5f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.REDSTONE_TORCH, null, Boolean.TRUE, null), HeatManager.toFixed(1.8f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.REDSTONE_WALL_TORCH, null, Boolean.TRUE, null), HeatManager.toFixed(1.8f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.TORCH, null, null, null), HeatManager.toFixed(1.8f)));
        rules.add(new LiquidSourceRule(new BlockMatcher(Blocks.WALL_TORCH, null, null, null), HeatManager.toFixed(1.8f)));
        return rules;
    }

    public HeatProps airProps() {
        return this.airProps;
    }

    public HeatProps defaultProps() {
        return this.defaultProps;
    }

    public List<HeatPropsRule> heatPropsRules() {
        return this.heatPropsRules;
    }

    public LiquidModel liquidModel() {
        return this.liquidModel;
    }

    public BiomeModel biomeModel() {
        return this.biomeModel;
    }

    public IgnitionModel ignitionModel() {
        return this.ignitionModel;
    }

    public PhaseModel phaseModel() {
        return this.phaseModel;
    }

    private static TagKey<Block> blockTag(String id) {
        return TagKey.create(Registries.BLOCK, net.minecraft.resources.ResourceLocation.parse(id));
    }

    private static TagKey<Fluid> fluidTag(String id) {
        return TagKey.create(Registries.FLUID, net.minecraft.resources.ResourceLocation.parse(id));
    }

    private static BiomeModel defaultBiomeModel() {
        List<BiomeRule> rules = new ArrayList<>();
        addBiomeRule(rules, "minecraft:badlands", 33);
        addBiomeRule(rules, "minecraft:bamboo_jungle", 27);
        addBiomeRule(rules, "minecraft:basalt_deltas", 72);
        addBiomeRule(rules, "minecraft:beach", 22);
        addBiomeRule(rules, "minecraft:birch_forest", 16);
        addBiomeRule(rules, "minecraft:cherry_grove", 11);
        addBiomeRule(rules, "minecraft:cold_ocean", 8);
        addBiomeRule(rules, "minecraft:crimson_forest", 58);
        addBiomeRule(rules, "minecraft:dark_forest", 16);
        addBiomeRule(rules, "minecraft:deep_cold_ocean", 6);
        addBiomeRule(rules, "minecraft:deep_dark", 9);
        addBiomeRule(rules, "minecraft:deep_frozen_ocean", -9);
        addBiomeRule(rules, "minecraft:deep_lukewarm_ocean", 19);
        addBiomeRule(rules, "minecraft:deep_ocean", 10);
        addBiomeRule(rules, "minecraft:desert", 35);
        addBiomeRule(rules, "minecraft:dripstone_caves", 14);
        addBiomeRule(rules, "minecraft:end_barrens", -9);
        addBiomeRule(rules, "minecraft:end_highlands", -8);
        addBiomeRule(rules, "minecraft:end_midlands", -7);
        addBiomeRule(rules, "minecraft:eroded_badlands", 34);
        addBiomeRule(rules, "minecraft:flower_forest", 17);
        addBiomeRule(rules, "minecraft:forest", 17);
        addBiomeRule(rules, "minecraft:frozen_ocean", -7);
        addBiomeRule(rules, "minecraft:frozen_peaks", -20);
        addBiomeRule(rules, "minecraft:frozen_river", -6);
        addBiomeRule(rules, "minecraft:grove", -8);
        addBiomeRule(rules, "minecraft:ice_spikes", -12);
        addBiomeRule(rules, "minecraft:jagged_peaks", -18);
        addBiomeRule(rules, "minecraft:jungle", 28);
        addBiomeRule(rules, "minecraft:lukewarm_ocean", 22);
        addBiomeRule(rules, "minecraft:lush_caves", 17);
        addBiomeRule(rules, "minecraft:mangrove_swamp", 26);
        addBiomeRule(rules, "minecraft:meadow", 12);
        addBiomeRule(rules, "minecraft:mushroom_fields", 18);
        addBiomeRule(rules, "minecraft:nether_wastes", 65);
        addBiomeRule(rules, "minecraft:ocean", 14);
        addBiomeRule(rules, "minecraft:old_growth_birch_forest", 15);
        addBiomeRule(rules, "minecraft:old_growth_pine_taiga", 8);
        addBiomeRule(rules, "minecraft:old_growth_spruce_taiga", 7);
        addBiomeRule(rules, "minecraft:plains", 18);
        addBiomeRule(rules, "minecraft:river", 14);
        addBiomeRule(rules, "minecraft:savanna", 29);
        addBiomeRule(rules, "minecraft:savanna_plateau", 27);
        addBiomeRule(rules, "minecraft:small_end_islands", -10);
        addBiomeRule(rules, "minecraft:snowy_beach", -3);
        addBiomeRule(rules, "minecraft:snowy_plains", -5);
        addBiomeRule(rules, "minecraft:snowy_slopes", -12);
        addBiomeRule(rules, "minecraft:snowy_taiga", -6);
        addBiomeRule(rules, "minecraft:soul_sand_valley", 45);
        addBiomeRule(rules, "minecraft:sparse_jungle", 25);
        addBiomeRule(rules, "minecraft:stony_peaks", 8);
        addBiomeRule(rules, "minecraft:stony_shore", 16);
        addBiomeRule(rules, "minecraft:sunflower_plains", 19);
        addBiomeRule(rules, "minecraft:swamp", 24);
        addBiomeRule(rules, "minecraft:taiga", 9);
        addBiomeRule(rules, "minecraft:the_end", -5);
        addBiomeRule(rules, "minecraft:the_void", -20);
        addBiomeRule(rules, "minecraft:warm_ocean", 28);
        addBiomeRule(rules, "minecraft:warped_forest", 42);
        addBiomeRule(rules, "minecraft:windswept_forest", 11);
        addBiomeRule(rules, "minecraft:windswept_gravelly_hills", 9);
        addBiomeRule(rules, "minecraft:windswept_hills", 10);
        addBiomeRule(rules, "minecraft:windswept_savanna", 25);
        addBiomeRule(rules, "minecraft:wooded_badlands", 30);
        return new BiomeModel(HeatManager.toFixed(18.0f), rules);
    }

    private static void addBiomeRule(List<BiomeRule> out, String biomeId, int ambientCelsius) {
        out.add(new BiomeRule(new BiomeMatcher(ResourceLocation.parse(biomeId), null), HeatManager.toFixed(ambientCelsius)));
    }

    public static final class BlockMatcher {
        private final Block block;
        private final TagKey<Block> tag;
        private final Boolean lit;
        private final Boolean soulBase;

        public BlockMatcher(Block block, TagKey<Block> tag, Boolean lit, Boolean soulBase) {
            this.block = block;
            this.tag = tag;
            this.lit = lit;
            this.soulBase = soulBase;
        }

        public Block block() {
            return this.block;
        }

        public TagKey<Block> tag() {
            return this.tag;
        }

        public Boolean lit() {
            return this.lit;
        }

        public Boolean soulBase() {
            return this.soulBase;
        }

        public boolean matches(BlockState state, boolean fireOnSoulBase) {
            if (this.block != null && !state.is(this.block)) {
                return false;
            }
            if (this.tag != null && !state.is(this.tag)) {
                return false;
            }
            if (this.lit != null) {
                if (!state.hasProperty(BlockStateProperties.LIT)) {
                    return false;
                }
                boolean value = state.getValue(BlockStateProperties.LIT);
                if (value != this.lit.booleanValue()) {
                    return false;
                }
            }
            return this.soulBase == null || this.soulBase.booleanValue() == fireOnSoulBase;
        }
    }

    public static final class FluidMatcher {
        private final Fluid fluid;
        private final TagKey<Fluid> tag;

        public FluidMatcher(Fluid fluid, TagKey<Fluid> tag) {
            this.fluid = fluid;
            this.tag = tag;
        }

        public Fluid fluid() {
            return this.fluid;
        }

        public TagKey<Fluid> tag() {
            return this.tag;
        }

        public boolean matches(FluidState state) {
            if (state == null || state.isEmpty()) {
                return false;
            }
            if (this.fluid != null && state.getType() != this.fluid) {
                return false;
            }
            return this.tag == null || state.is(this.tag);
        }
    }

    public static final class HeatPropsRule {
        private final BlockMatcher matcher;
        private final HeatProps props;

        public HeatPropsRule(BlockMatcher matcher, HeatProps props) {
            this.matcher = matcher;
            this.props = props;
        }

        public BlockMatcher matcher() {
            return this.matcher;
        }

        public HeatProps props() {
            return this.props;
        }

        public boolean matches(BlockState state, boolean fireOnSoulBase) {
            return this.matcher.matches(state, fireOnSoulBase);
        }
    }

    public static final class LiquidSourceRule {
        private final BlockMatcher matcher;
        private final int contributionFixed;

        public LiquidSourceRule(BlockMatcher matcher, int contributionFixed) {
            this.matcher = matcher;
            this.contributionFixed = Math.max(0, contributionFixed);
        }

        public BlockMatcher matcher() {
            return this.matcher;
        }

        public int contributionFixed() {
            return this.contributionFixed;
        }

        public boolean matches(BlockState state) {
            return this.matcher.matches(state, false);
        }
    }

    public static final class LiquidProfile {
        private final HeatProps heatProps;
        private final int surfaceScanSteps;
        private final int inertiaEvictIntervalTicks;
        private final int inertiaEvictIdleTicks;
        private final int baseCoolOffsetFixed;
        private final int surfaceSolarMaxFixed;
        private final int deepCoolPerBlockFixed;
        private final int deepCoolMaxFixed;
        private final int heatSourceMaxFixed;
        private final int targetMinFixed;
        private final int targetMaxFixed;
        private final float inertiaWarmRate;
        private final float inertiaCoolRate;
        private final List<LiquidSourceRule> sourceRules;

        public LiquidProfile(
                HeatProps heatProps,
                int surfaceScanSteps,
                int inertiaEvictIntervalTicks,
                int inertiaEvictIdleTicks,
                int baseCoolOffsetFixed,
                int surfaceSolarMaxFixed,
                int deepCoolPerBlockFixed,
                int deepCoolMaxFixed,
                int heatSourceMaxFixed,
                int targetMinFixed,
                int targetMaxFixed,
                float inertiaWarmRate,
                float inertiaCoolRate,
                List<LiquidSourceRule> sourceRules
        ) {
            this.heatProps = heatProps;
            this.surfaceScanSteps = Math.max(1, surfaceScanSteps);
            this.inertiaEvictIntervalTicks = Math.max(1, inertiaEvictIntervalTicks);
            this.inertiaEvictIdleTicks = Math.max(20, inertiaEvictIdleTicks);
            this.baseCoolOffsetFixed = baseCoolOffsetFixed;
            this.surfaceSolarMaxFixed = Math.max(0, surfaceSolarMaxFixed);
            this.deepCoolPerBlockFixed = Math.max(0, deepCoolPerBlockFixed);
            this.deepCoolMaxFixed = Math.max(0, deepCoolMaxFixed);
            this.heatSourceMaxFixed = Math.max(0, heatSourceMaxFixed);
            this.targetMinFixed = Math.min(targetMinFixed, targetMaxFixed);
            this.targetMaxFixed = Math.max(targetMinFixed, targetMaxFixed);
            this.inertiaWarmRate = Math.max(0.0f, inertiaWarmRate);
            this.inertiaCoolRate = Math.max(0.0f, inertiaCoolRate);
            this.sourceRules = List.copyOf(sourceRules);
        }

        public HeatProps heatProps() {
            return this.heatProps;
        }

        public int surfaceScanSteps() {
            return this.surfaceScanSteps;
        }

        public int inertiaEvictIntervalTicks() {
            return this.inertiaEvictIntervalTicks;
        }

        public int inertiaEvictIdleTicks() {
            return this.inertiaEvictIdleTicks;
        }

        public int baseCoolOffsetFixed() {
            return this.baseCoolOffsetFixed;
        }

        public int surfaceSolarMaxFixed() {
            return this.surfaceSolarMaxFixed;
        }

        public int deepCoolPerBlockFixed() {
            return this.deepCoolPerBlockFixed;
        }

        public int deepCoolMaxFixed() {
            return this.deepCoolMaxFixed;
        }

        public int heatSourceMaxFixed() {
            return this.heatSourceMaxFixed;
        }

        public int targetMinFixed() {
            return this.targetMinFixed;
        }

        public int targetMaxFixed() {
            return this.targetMaxFixed;
        }

        public float inertiaWarmRate() {
            return this.inertiaWarmRate;
        }

        public float inertiaCoolRate() {
            return this.inertiaCoolRate;
        }

        public List<LiquidSourceRule> sourceRules() {
            return this.sourceRules;
        }
    }

    public static final class LiquidEntry {
        private final FluidMatcher matcher;
        private final LiquidProfile profile;

        public LiquidEntry(FluidMatcher matcher, LiquidProfile profile) {
            this.matcher = matcher;
            this.profile = profile;
        }

        public FluidMatcher matcher() {
            return this.matcher;
        }

        public LiquidProfile profile() {
            return this.profile;
        }

        public boolean matches(FluidState state) {
            return this.matcher.matches(state);
        }
    }

    public static final class LiquidModel {
        private final LiquidProfile defaultLiquid;
        private final List<LiquidEntry> entries;

        public LiquidModel(LiquidProfile defaultLiquid, List<LiquidEntry> entries) {
            this.defaultLiquid = defaultLiquid;
            this.entries = List.copyOf(entries);
        }

        public LiquidProfile defaultLiquid() {
            return this.defaultLiquid;
        }

        public List<LiquidEntry> entries() {
            return this.entries;
        }

        public LiquidProfile resolveProfile(FluidState fluidState) {
            if (fluidState == null || fluidState.isEmpty()) {
                return null;
            }
            for (LiquidEntry entry : this.entries) {
                if (entry.matches(fluidState)) {
                    return entry.profile();
                }
            }
            return this.defaultLiquid;
        }
    }

    public static final class BiomeMatcher {
        private final ResourceLocation biomeId;
        private final TagKey<Biome> tag;

        public BiomeMatcher(ResourceLocation biomeId, TagKey<Biome> tag) {
            this.biomeId = biomeId;
            this.tag = tag;
        }

        public ResourceLocation biomeId() {
            return this.biomeId;
        }

        public TagKey<Biome> tag() {
            return this.tag;
        }

        public boolean matches(Holder<Biome> biomeHolder) {
            if (this.biomeId != null) {
                ResourceLocation currentId = biomeHolder.unwrapKey().map(ResourceKey::location).orElse(null);
                if (!this.biomeId.equals(currentId)) {
                    return false;
                }
            }
            return this.tag == null || biomeHolder.is(this.tag);
        }
    }

    public static final class BiomeRule {
        private final BiomeMatcher matcher;
        private final int ambientFixed;

        public BiomeRule(BiomeMatcher matcher, int ambientFixed) {
            this.matcher = matcher;
            this.ambientFixed = ambientFixed;
        }

        public BiomeMatcher matcher() {
            return this.matcher;
        }

        public int ambientFixed() {
            return this.ambientFixed;
        }

        public boolean matches(Holder<Biome> biomeHolder) {
            return this.matcher.matches(biomeHolder);
        }
    }

    public static final class BiomeModel {
        private final int defaultAmbientFixed;
        private final List<BiomeRule> entries;

        public BiomeModel(int defaultAmbientFixed, List<BiomeRule> entries) {
            this.defaultAmbientFixed = defaultAmbientFixed;
            this.entries = List.copyOf(entries);
        }

        public int defaultAmbientFixed() {
            return this.defaultAmbientFixed;
        }

        public List<BiomeRule> entries() {
            return this.entries;
        }

        public int resolveAmbientFixed(Holder<Biome> biomeHolder) {
            for (BiomeRule entry : this.entries) {
                if (entry.matches(biomeHolder)) {
                    return entry.ambientFixed();
                }
            }
            return this.defaultAmbientFixed;
        }
    }

    public static final class IgnitionModel {
        private final int airThresholdFixed;
        private final boolean respectFireTickGameRule;
        private final boolean useIgnitableTag;
        private final boolean useVanillaFlammable;
        private final Block resultBlock;
        private final boolean preferSoulFireOnSoulBase;

        public IgnitionModel(
                int airThresholdFixed,
                boolean respectFireTickGameRule,
                boolean useIgnitableTag,
                boolean useVanillaFlammable,
                Block resultBlock,
                boolean preferSoulFireOnSoulBase
        ) {
            this.airThresholdFixed = airThresholdFixed;
            this.respectFireTickGameRule = respectFireTickGameRule;
            this.useIgnitableTag = useIgnitableTag;
            this.useVanillaFlammable = useVanillaFlammable;
            this.resultBlock = resultBlock == null ? Blocks.FIRE : resultBlock;
            this.preferSoulFireOnSoulBase = preferSoulFireOnSoulBase;
        }

        public int airThresholdFixed() {
            return this.airThresholdFixed;
        }

        public boolean respectFireTickGameRule() {
            return this.respectFireTickGameRule;
        }

        public boolean useIgnitableTag() {
            return this.useIgnitableTag;
        }

        public boolean useVanillaFlammable() {
            return this.useVanillaFlammable;
        }

        public Block resultBlock() {
            return this.resultBlock;
        }

        public boolean preferSoulFireOnSoulBase() {
            return this.preferSoulFireOnSoulBase;
        }
    }

    public enum ThresholdDirection {
        ABOVE,
        BELOW
    }

    public static final class PhaseRule {
        private final BlockMatcher matcher;
        private final ThresholdDirection direction;
        private final int thresholdFixed;
        private final Block resultBlock;
        private final Block ultraWarmResultBlock;
        private final boolean requireSourceFluid;

        public PhaseRule(
                BlockMatcher matcher,
                ThresholdDirection direction,
                int thresholdFixed,
                Block resultBlock,
                Block ultraWarmResultBlock,
                boolean requireSourceFluid
        ) {
            this.matcher = matcher;
            this.direction = direction;
            this.thresholdFixed = thresholdFixed;
            this.resultBlock = resultBlock;
            this.ultraWarmResultBlock = ultraWarmResultBlock;
            this.requireSourceFluid = requireSourceFluid;
        }

        public BlockMatcher matcher() {
            return this.matcher;
        }

        public ThresholdDirection direction() {
            return this.direction;
        }

        public int thresholdFixed() {
            return this.thresholdFixed;
        }

        public Block resultBlock() {
            return this.resultBlock;
        }

        public Block ultraWarmResultBlock() {
            return this.ultraWarmResultBlock;
        }

        public boolean requireSourceFluid() {
            return this.requireSourceFluid;
        }

        public boolean matchesState(BlockState state) {
            return this.matcher.matches(state, false);
        }

        public boolean crossedThreshold(int oldTempFixed, int newTempFixed) {
            if (this.direction == ThresholdDirection.ABOVE) {
                return oldTempFixed <= this.thresholdFixed && newTempFixed > this.thresholdFixed;
            }
            return oldTempFixed >= this.thresholdFixed && newTempFixed < this.thresholdFixed;
        }

        public boolean meetsThreshold(int tempFixed) {
            if (this.direction == ThresholdDirection.ABOVE) {
                return tempFixed > this.thresholdFixed;
            }
            return tempFixed < this.thresholdFixed;
        }

        public Block resolveResultBlock(boolean ultraWarm) {
            if (ultraWarm && this.ultraWarmResultBlock != null) {
                return this.ultraWarmResultBlock;
            }
            return this.resultBlock;
        }
    }

    public static final class PhaseModel {
        private final boolean requirePcmTag;
        private final List<PhaseRule> rules;

        public PhaseModel(boolean requirePcmTag, List<PhaseRule> rules) {
            this.requirePcmTag = requirePcmTag;
            this.rules = List.copyOf(rules);
        }

        public boolean requirePcmTag() {
            return this.requirePcmTag;
        }

        public List<PhaseRule> rules() {
            return this.rules;
        }

        public boolean crossedAnyThreshold(int oldTempFixed, int newTempFixed) {
            for (PhaseRule rule : this.rules) {
                if (rule.crossedThreshold(oldTempFixed, newTempFixed)) {
                    return true;
                }
            }
            return false;
        }
    }
}
