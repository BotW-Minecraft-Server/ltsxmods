package link.botwmcs.ltsxlogica.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import link.botwmcs.ltsxlogica.heat.HeatManager;
import link.botwmcs.ltsxlogica.heat.HeatProps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

/**
 * Datapack reload listener for the heat model.
 */
public final class HeatDataReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "ltsxlogica/heat_model";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public HeatDataReloadListener() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        HeatModelData resolved = HeatModelData.defaults();

        if (!entries.isEmpty()) {
            List<Map.Entry<ResourceLocation, JsonElement>> sorted = new ArrayList<>(entries.entrySet());
            sorted.sort(Comparator.comparing(entry -> entry.getKey().toString()));

            for (Map.Entry<ResourceLocation, JsonElement> entry : sorted) {
                if (!entry.getValue().isJsonObject()) {
                    LTSXLogicA.LOGGER.warn("Skipping heat model file {} because root is not a JSON object", entry.getKey());
                    continue;
                }
                try {
                    resolved = mergeRoot(entry.getValue().getAsJsonObject(), resolved);
                } catch (Exception ex) {
                    LTSXLogicA.LOGGER.warn("Failed to parse heat model file {}. Keeping previous values.", entry.getKey(), ex);
                }
            }
        }

        HeatDataRegistry.setModel(resolved);
        LTSXLogicA.LOGGER.info("Heat model loaded ({} file(s), revision={})", entries.size(), HeatDataRegistry.revision());
    }

    private static HeatModelData mergeRoot(JsonObject root, HeatModelData fallback) {
        HeatProps airProps = fallback.airProps();
        if (root.has("air_props") && root.get("air_props").isJsonObject()) {
            airProps = parseHeatProps(root.getAsJsonObject("air_props"), airProps);
        }

        HeatProps defaultProps = fallback.defaultProps();
        if (root.has("default_props") && root.get("default_props").isJsonObject()) {
            defaultProps = parseHeatProps(root.getAsJsonObject("default_props"), defaultProps);
        }

        List<HeatModelData.HeatPropsRule> heatPropsRules = fallback.heatPropsRules();
        if (root.has("heat_props_rules") && root.get("heat_props_rules").isJsonArray()) {
            boolean append = getBoolean(root, "append_heat_props_rules", false);
            heatPropsRules = parseHeatPropsRules(root.getAsJsonArray("heat_props_rules"), append ? heatPropsRules : List.of());
        }

        HeatModelData.LiquidModel liquidModel = fallback.liquidModel();
        if (root.has("liquids") && root.get("liquids").isJsonObject()) {
            liquidModel = parseLiquidModel(root.getAsJsonObject("liquids"), liquidModel);
        } else if (root.has("water") && root.get("water").isJsonObject()) {
            // Legacy compatibility: old "water" block maps to "liquids.default_liquid".
            HeatModelData.LiquidProfile profile = parseLiquidProfile(root.getAsJsonObject("water"), liquidModel.defaultLiquid());
            liquidModel = new HeatModelData.LiquidModel(profile, liquidModel.entries());
        }

        HeatModelData.BiomeModel biomeModel = fallback.biomeModel();
        if (root.has("biomes") && root.get("biomes").isJsonObject()) {
            biomeModel = parseBiomeModel(root.getAsJsonObject("biomes"), biomeModel);
        }

        HeatModelData.IgnitionModel ignitionModel = fallback.ignitionModel();
        if (root.has("ignition") && root.get("ignition").isJsonObject()) {
            ignitionModel = parseIgnitionModel(root.getAsJsonObject("ignition"), ignitionModel);
        }

        HeatModelData.PhaseModel phaseModel = fallback.phaseModel();
        if (root.has("phase_change") && root.get("phase_change").isJsonObject()) {
            phaseModel = parsePhaseModel(root.getAsJsonObject("phase_change"), phaseModel);
        }

        return new HeatModelData(airProps, defaultProps, heatPropsRules, liquidModel, biomeModel, ignitionModel, phaseModel);
    }

    private static List<HeatModelData.HeatPropsRule> parseHeatPropsRules(JsonArray array, List<HeatModelData.HeatPropsRule> baseRules) {
        List<HeatModelData.HeatPropsRule> rules = new ArrayList<>(baseRules);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject ruleObj = element.getAsJsonObject();
            JsonObject matchObj = GsonHelper.getAsJsonObject(ruleObj, "match", new JsonObject());
            JsonObject propsObj = GsonHelper.getAsJsonObject(ruleObj, "props", new JsonObject());
            HeatModelData.BlockMatcher matcher = parseBlockMatcher(matchObj, new HeatModelData.BlockMatcher(null, null, null, null));
            HeatProps props = parseHeatProps(propsObj, HeatProps.AIR);
            rules.add(new HeatModelData.HeatPropsRule(matcher, props));
        }
        return List.copyOf(rules);
    }

    private static HeatProps parseHeatProps(JsonObject obj, HeatProps fallback) {
        float conductivityK = getFloat(obj, "conductivity_k", fallback.conductivityK);
        float capacityC = getFloat(obj, "capacity_c", fallback.capacityC);
        float generationQ = getFloat(obj, "generation_q", fallback.generationQ);
        float relaxR = getFloat(obj, "relax_r", fallback.relaxR);
        float convectiveH = getFloat(obj, "convective_h", fallback.convectiveH);
        int updatePeriodTicks = getInt(obj, "update_period_ticks", fallback.updatePeriodTicks);
        boolean airLike = getBoolean(obj, "air_like", fallback.airLike);

        boolean hasTarget = fallback.hasTarget();
        float targetCelsius = fallback.hasTarget() ? HeatManager.toCelsius(fallback.targetFixed) : 0.0f;
        if (obj.has("target_celsius")) {
            targetCelsius = getFloat(obj, "target_celsius", targetCelsius);
            hasTarget = true;
        }

        float sourceStrengthS = getFloat(obj, "source_strength_s", fallback.sourceStrengthS);
        if (sourceStrengthS <= 0.0f) {
            hasTarget = false;
        }

        if (hasTarget) {
            return HeatProps.of(
                    conductivityK,
                    capacityC,
                    generationQ,
                    relaxR,
                    targetCelsius,
                    sourceStrengthS,
                    convectiveH,
                    updatePeriodTicks,
                    airLike
            );
        }

        return HeatProps.ofNoTarget(
                conductivityK,
                capacityC,
                generationQ,
                relaxR,
                convectiveH,
                updatePeriodTicks,
                airLike
        );
    }

    private static HeatModelData.LiquidModel parseLiquidModel(JsonObject obj, HeatModelData.LiquidModel fallback) {
        HeatModelData.LiquidProfile defaultLiquid = fallback.defaultLiquid();
        if (obj.has("default_liquid") && obj.get("default_liquid").isJsonObject()) {
            defaultLiquid = parseLiquidProfile(obj.getAsJsonObject("default_liquid"), defaultLiquid);
        }

        List<HeatModelData.LiquidEntry> entries = fallback.entries();
        if (obj.has("entries") && obj.get("entries").isJsonArray()) {
            boolean append = getBoolean(obj, "append_entries", false);
            entries = parseLiquidEntries(obj.getAsJsonArray("entries"), append ? entries : List.of(), defaultLiquid);
        }

        return new HeatModelData.LiquidModel(defaultLiquid, entries);
    }

    private static List<HeatModelData.LiquidEntry> parseLiquidEntries(
            JsonArray array,
            List<HeatModelData.LiquidEntry> baseEntries,
            HeatModelData.LiquidProfile defaultProfile
    ) {
        List<HeatModelData.LiquidEntry> entries = new ArrayList<>(baseEntries);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            JsonObject matchObj = GsonHelper.getAsJsonObject(obj, "match", new JsonObject());
            HeatModelData.FluidMatcher matcher = parseFluidMatcher(matchObj, new HeatModelData.FluidMatcher(null, null));
            if (matcher.fluid() == null && matcher.tag() == null) {
                LTSXLogicA.LOGGER.warn("Skipping liquid entry because no fluid/tag matcher was provided");
                continue;
            }

            JsonObject profileObj = obj.has("profile") && obj.get("profile").isJsonObject()
                    ? obj.getAsJsonObject("profile")
                    : obj;
            HeatModelData.LiquidProfile profile = parseLiquidProfile(profileObj, defaultProfile);
            entries.add(new HeatModelData.LiquidEntry(matcher, profile));
        }
        return List.copyOf(entries);
    }

    private static HeatModelData.LiquidProfile parseLiquidProfile(JsonObject obj, HeatModelData.LiquidProfile fallback) {
        HeatProps heatProps = fallback.heatProps();
        if (obj.has("heat_props") && obj.get("heat_props").isJsonObject()) {
            heatProps = parseHeatProps(obj.getAsJsonObject("heat_props"), heatProps);
        }

        int surfaceScanSteps = getInt(obj, "surface_scan_steps", fallback.surfaceScanSteps());
        int inertiaEvictIntervalTicks = getInt(obj, "inertia_evict_interval_ticks", fallback.inertiaEvictIntervalTicks());
        int inertiaEvictIdleTicks = getInt(obj, "inertia_evict_idle_ticks", fallback.inertiaEvictIdleTicks());
        int baseCoolOffsetFixed = parseCelsiusAsFixed(obj, "base_cool_offset_celsius", fallback.baseCoolOffsetFixed());
        int surfaceSolarMaxFixed = parseCelsiusAsFixed(obj, "surface_solar_max_celsius", fallback.surfaceSolarMaxFixed());
        int deepCoolPerBlockFixed = parseCelsiusAsFixed(obj, "deep_cool_per_block_celsius", fallback.deepCoolPerBlockFixed());
        int deepCoolMaxFixed = parseCelsiusAsFixed(obj, "deep_cool_max_celsius", fallback.deepCoolMaxFixed());
        int heatSourceMaxFixed = parseCelsiusAsFixed(obj, "heat_source_max_celsius", fallback.heatSourceMaxFixed());
        int targetMinFixed = parseCelsiusAsFixed(obj, "target_min_celsius", fallback.targetMinFixed());
        int targetMaxFixed = parseCelsiusAsFixed(obj, "target_max_celsius", fallback.targetMaxFixed());
        float inertiaWarmRate = getFloat(obj, "inertia_warm_rate", fallback.inertiaWarmRate());
        float inertiaCoolRate = getFloat(obj, "inertia_cool_rate", fallback.inertiaCoolRate());

        List<HeatModelData.LiquidSourceRule> sourceRules = fallback.sourceRules();
        if (obj.has("source_rules") && obj.get("source_rules").isJsonArray()) {
            boolean append = getBoolean(obj, "append_source_rules", false);
            sourceRules = parseLiquidSourceRules(obj.getAsJsonArray("source_rules"), append ? sourceRules : List.of());
        }

        return new HeatModelData.LiquidProfile(
                heatProps,
                surfaceScanSteps,
                inertiaEvictIntervalTicks,
                inertiaEvictIdleTicks,
                baseCoolOffsetFixed,
                surfaceSolarMaxFixed,
                deepCoolPerBlockFixed,
                deepCoolMaxFixed,
                heatSourceMaxFixed,
                targetMinFixed,
                targetMaxFixed,
                inertiaWarmRate,
                inertiaCoolRate,
                sourceRules
        );
    }

    private static List<HeatModelData.LiquidSourceRule> parseLiquidSourceRules(
            JsonArray array,
            List<HeatModelData.LiquidSourceRule> baseRules
    ) {
        List<HeatModelData.LiquidSourceRule> rules = new ArrayList<>(baseRules);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            JsonObject matchObj = GsonHelper.getAsJsonObject(obj, "match", new JsonObject());
            HeatModelData.BlockMatcher matcher = parseBlockMatcher(matchObj, new HeatModelData.BlockMatcher(null, null, null, null));
            int contributionFixed = parseCelsiusAsFixed(obj, "contribution_celsius", 0);
            rules.add(new HeatModelData.LiquidSourceRule(matcher, contributionFixed));
        }
        return List.copyOf(rules);
    }

    private static HeatModelData.BiomeModel parseBiomeModel(JsonObject obj, HeatModelData.BiomeModel fallback) {
        int defaultAmbientFixed = fallback.defaultAmbientFixed();
        if (obj.has("default_biome") && obj.get("default_biome").isJsonObject()) {
            JsonObject defaultObj = obj.getAsJsonObject("default_biome");
            defaultAmbientFixed = parseCelsiusAsFixed(defaultObj, "ambient_celsius", defaultAmbientFixed);
        } else if (obj.has("default_ambient_celsius")) {
            defaultAmbientFixed = parseCelsiusAsFixed(obj, "default_ambient_celsius", defaultAmbientFixed);
        }

        List<HeatModelData.BiomeRule> entries = fallback.entries();
        if (obj.has("entries") && obj.get("entries").isJsonArray()) {
            boolean append = getBoolean(obj, "append_entries", false);
            entries = parseBiomeRules(obj.getAsJsonArray("entries"), append ? entries : List.of());
        }

        return new HeatModelData.BiomeModel(defaultAmbientFixed, entries);
    }

    private static List<HeatModelData.BiomeRule> parseBiomeRules(JsonArray array, List<HeatModelData.BiomeRule> baseRules) {
        List<HeatModelData.BiomeRule> rules = new ArrayList<>(baseRules);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            JsonObject matchObj = GsonHelper.getAsJsonObject(obj, "match", new JsonObject());
            HeatModelData.BiomeMatcher matcher = parseBiomeMatcher(matchObj, new HeatModelData.BiomeMatcher(null, null));
            if (matcher.biomeId() == null && matcher.tag() == null) {
                LTSXLogicA.LOGGER.warn("Skipping biome entry because no biome/tag matcher was provided");
                continue;
            }
            int ambientFixed = parseCelsiusAsFixed(obj, "ambient_celsius", HeatManager.toFixed(18.0f));
            rules.add(new HeatModelData.BiomeRule(matcher, ambientFixed));
        }
        return List.copyOf(rules);
    }

    private static HeatModelData.IgnitionModel parseIgnitionModel(JsonObject obj, HeatModelData.IgnitionModel fallback) {
        int airThresholdFixed = parseCelsiusAsFixed(obj, "air_threshold_celsius", fallback.airThresholdFixed());
        boolean respectFireTick = getBoolean(obj, "respect_fire_tick_gamerule", fallback.respectFireTickGameRule());
        boolean useIgnitableTag = getBoolean(obj, "use_ignitable_tag", fallback.useIgnitableTag());
        boolean useVanillaFlammable = getBoolean(obj, "use_vanilla_flammable", fallback.useVanillaFlammable());
        boolean preferSoulFireOnSoulBase = getBoolean(
                obj,
                "prefer_soul_fire_on_soul_base",
                fallback.preferSoulFireOnSoulBase()
        );

        Block resultBlock = fallback.resultBlock();
        if (obj.has("result_block")) {
            Block parsed = parseBlock(getString(obj, "result_block", null));
            if (parsed != null) {
                resultBlock = parsed;
            }
        }

        return new HeatModelData.IgnitionModel(
                airThresholdFixed,
                respectFireTick,
                useIgnitableTag,
                useVanillaFlammable,
                resultBlock,
                preferSoulFireOnSoulBase
        );
    }

    private static HeatModelData.PhaseModel parsePhaseModel(JsonObject obj, HeatModelData.PhaseModel fallback) {
        boolean requirePcmTag = getBoolean(obj, "require_pcm_tag", fallback.requirePcmTag());
        List<HeatModelData.PhaseRule> rules = fallback.rules();

        if (obj.has("rules") && obj.get("rules").isJsonArray()) {
            boolean append = getBoolean(obj, "append_rules", false);
            rules = parsePhaseRules(obj.getAsJsonArray("rules"), append ? rules : List.of());
        }

        return new HeatModelData.PhaseModel(requirePcmTag, rules);
    }

    private static List<HeatModelData.PhaseRule> parsePhaseRules(JsonArray array, List<HeatModelData.PhaseRule> baseRules) {
        List<HeatModelData.PhaseRule> rules = new ArrayList<>(baseRules);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            JsonObject matchObj = GsonHelper.getAsJsonObject(obj, "match", new JsonObject());
            HeatModelData.BlockMatcher matcher = parseBlockMatcher(matchObj, new HeatModelData.BlockMatcher(null, null, null, null));

            String directionRaw = getString(obj, "direction", "above");
            HeatModelData.ThresholdDirection direction = "below".equals(directionRaw.toLowerCase(Locale.ROOT))
                    ? HeatModelData.ThresholdDirection.BELOW
                    : HeatModelData.ThresholdDirection.ABOVE;
            int thresholdFixed = parseCelsiusAsFixed(obj, "threshold_celsius", HeatManager.toFixed(0.0f));

            Block resultBlock = parseBlock(getString(obj, "result_block", "minecraft:air"));
            if (resultBlock == null) {
                resultBlock = Blocks.AIR;
            }

            Block ultraWarmResult = null;
            if (obj.has("ultrawarm_result_block")) {
                ultraWarmResult = parseBlock(getString(obj, "ultrawarm_result_block", null));
            }

            boolean requireSourceFluid = getBoolean(obj, "require_source_fluid", false);
            rules.add(new HeatModelData.PhaseRule(
                    matcher,
                    direction,
                    thresholdFixed,
                    resultBlock,
                    ultraWarmResult,
                    requireSourceFluid
            ));
        }
        return List.copyOf(rules);
    }

    private static HeatModelData.BlockMatcher parseBlockMatcher(JsonObject obj, HeatModelData.BlockMatcher fallback) {
        Block block = fallback.block();
        TagKey<Block> tag = fallback.tag();

        if (obj.has("block")) {
            String raw = getString(obj, "block", null);
            if (raw != null && raw.startsWith("#")) {
                TagKey<Block> parsedTag = parseBlockTag(raw.substring(1));
                if (parsedTag != null) {
                    tag = parsedTag;
                    block = null;
                }
            } else {
                Block parsedBlock = parseBlock(raw);
                if (parsedBlock != null) {
                    block = parsedBlock;
                }
            }
        }

        if (obj.has("tag")) {
            TagKey<Block> parsedTag = parseBlockTag(getString(obj, "tag", null));
            if (parsedTag != null) {
                tag = parsedTag;
            }
        }

        Boolean lit = fallback.lit();
        if (obj.has("lit")) {
            lit = getBoolean(obj, "lit", false);
        }

        Boolean soulBase = fallback.soulBase();
        if (obj.has("soul_base")) {
            soulBase = getBoolean(obj, "soul_base", false);
        }

        return new HeatModelData.BlockMatcher(block, tag, lit, soulBase);
    }

    private static HeatModelData.FluidMatcher parseFluidMatcher(JsonObject obj, HeatModelData.FluidMatcher fallback) {
        Fluid fluid = fallback.fluid();
        TagKey<Fluid> tag = fallback.tag();

        if (obj.has("fluid")) {
            String raw = getString(obj, "fluid", null);
            if (raw != null && raw.startsWith("#")) {
                TagKey<Fluid> parsedTag = parseFluidTag(raw.substring(1));
                if (parsedTag != null) {
                    tag = parsedTag;
                    fluid = null;
                }
            } else {
                Fluid parsedFluid = parseFluid(raw);
                if (parsedFluid != null) {
                    fluid = parsedFluid;
                }
            }
        }

        if (obj.has("tag")) {
            TagKey<Fluid> parsedTag = parseFluidTag(getString(obj, "tag", null));
            if (parsedTag != null) {
                tag = parsedTag;
            }
        }

        return new HeatModelData.FluidMatcher(fluid, tag);
    }

    private static HeatModelData.BiomeMatcher parseBiomeMatcher(JsonObject obj, HeatModelData.BiomeMatcher fallback) {
        ResourceLocation biomeId = fallback.biomeId();
        TagKey<Biome> tag = fallback.tag();

        if (obj.has("biome")) {
            String raw = getString(obj, "biome", null);
            if (raw != null && raw.startsWith("#")) {
                TagKey<Biome> parsedTag = parseBiomeTag(raw.substring(1));
                if (parsedTag != null) {
                    tag = parsedTag;
                    biomeId = null;
                }
            } else {
                ResourceLocation parsedBiome = parseBiomeId(raw);
                if (parsedBiome != null) {
                    biomeId = parsedBiome;
                }
            }
        }

        if (obj.has("tag")) {
            TagKey<Biome> parsedTag = parseBiomeTag(getString(obj, "tag", null));
            if (parsedTag != null) {
                tag = parsedTag;
            }
        }

        return new HeatModelData.BiomeMatcher(biomeId, tag);
    }

    private static Block parseBlock(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            LTSXLogicA.LOGGER.warn("Invalid block id '{}' in heat model", raw);
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(id).orElseGet(() -> {
            LTSXLogicA.LOGGER.warn("Unknown block id '{}' in heat model", raw);
            return null;
        });
    }

    private static Fluid parseFluid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            LTSXLogicA.LOGGER.warn("Invalid fluid id '{}' in heat model", raw);
            return null;
        }
        return BuiltInRegistries.FLUID.getOptional(id).orElseGet(() -> {
            LTSXLogicA.LOGGER.warn("Unknown fluid id '{}' in heat model", raw);
            return null;
        });
    }

    private static TagKey<Block> parseBlockTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            LTSXLogicA.LOGGER.warn("Invalid block tag id '{}' in heat model", raw);
            return null;
        }
        return TagKey.create(Registries.BLOCK, id);
    }

    private static TagKey<Fluid> parseFluidTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            LTSXLogicA.LOGGER.warn("Invalid fluid tag id '{}' in heat model", raw);
            return null;
        }
        return TagKey.create(Registries.FLUID, id);
    }

    private static ResourceLocation parseBiomeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            LTSXLogicA.LOGGER.warn("Invalid biome id '{}' in heat model", raw);
            return null;
        }
        return id;
    }

    private static TagKey<Biome> parseBiomeTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            LTSXLogicA.LOGGER.warn("Invalid biome tag id '{}' in heat model", raw);
            return null;
        }
        return TagKey.create(Registries.BIOME, id);
    }

    private static int parseCelsiusAsFixed(JsonObject obj, String field, int fallbackFixed) {
        if (!obj.has(field)) {
            return fallbackFixed;
        }
        float celsius = getFloat(obj, field, HeatManager.toCelsius(fallbackFixed));
        return HeatManager.toFixed(celsius);
    }

    private static String getString(JsonObject obj, String field, String fallback) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return fallback;
        }
        return GsonHelper.getAsString(obj, field, fallback);
    }

    private static boolean getBoolean(JsonObject obj, String field, boolean fallback) {
        return GsonHelper.getAsBoolean(obj, field, fallback);
    }

    private static int getInt(JsonObject obj, String field, int fallback) {
        return GsonHelper.getAsInt(obj, field, fallback);
    }

    private static float getFloat(JsonObject obj, String field, float fallback) {
        return GsonHelper.getAsFloat(obj, field, fallback);
    }
}
