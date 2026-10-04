package link.botwmcs.ltsxlogica.data.mastery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import link.botwmcs.ltsxlogica.api.mastery.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** default.json is the datapack baseline; additional bundles replace whole entries by explicit ID. */
public final class MasteryDefinitionParser {
    private MasteryDefinitionParser() {}

    public static MasteryDefinitions parse(Map<ResourceLocation, JsonElement> resources) {
        Map<ResourceLocation, MasteryDefinition> masteries = new HashMap<>();
        Map<ResourceLocation, RuneDefinition> runes = new HashMap<>();
        var sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(Comparator.<Map.Entry<ResourceLocation, JsonElement>, Boolean>comparing(
                e -> !e.getKey().getPath().equals("default") && !e.getKey().getPath().endsWith("/default"))
                .thenComparing(e -> e.getKey().toString()));
        for (var resource : sorted) {
            try {
                JsonObject root = resource.getValue().getAsJsonObject();
                if (integer(root, "schema_version", 1) != 1) throw new IllegalArgumentException("Unsupported schema_version");
                if (root.has("masteries")) for (var entry : root.getAsJsonObject("masteries").entrySet()) {
                    ResourceLocation id = ResourceLocation.parse(entry.getKey());
                    JsonObject value = entry.getValue().getAsJsonObject();
                    if (!GsonHelper.getAsBoolean(value, "enabled", true)) masteries.remove(id);
                    else masteries.put(id, mastery(id, value));
                }
                if (root.has("runes")) for (var entry : root.getAsJsonObject("runes").entrySet()) {
                    ResourceLocation id = ResourceLocation.parse(entry.getKey());
                    JsonObject value = entry.getValue().getAsJsonObject();
                    if (!GsonHelper.getAsBoolean(value, "enabled", true)) runes.remove(id);
                    else runes.put(id, rune(id, value));
                }
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Invalid mastery bundle " + resource.getKey() + ": " + ex.getMessage(), ex);
            }
        }
        return new MasteryDefinitions(masteries, runes);
    }

    private static MasteryDefinition mastery(ResourceLocation id, JsonObject obj) {
        MasteryStage unlock = stage(1, obj.has("unlock") ? obj.getAsJsonObject("unlock") : new JsonObject());
        Map<Integer, MasteryStage> stages = new HashMap<>();
        JsonArray array = GsonHelper.getAsJsonArray(obj, "stages");
        for (JsonElement element : array) {
            JsonObject value = element.getAsJsonObject();
            int from = integer(value, "from", integer(value, "level", 2));
            int to = integer(value, "to", from);
            if (from < 2 || to > 100 || from > to) throw new IllegalArgumentException("Stage range must be within 2..100");
            for (int level = from; level <= to; level++) {
                if (stages.putIfAbsent(level, stage(level, value)) != null) throw new IllegalArgumentException("Overlapping stage " + level);
            }
        }
        return new MasteryDefinition(id, GsonHelper.getAsString(obj, "name", id.toString()),
                ids(GsonHelper.getAsJsonArray(obj, "allowed_series")), unlock, stages);
    }

    private static MasteryStage stage(int level, JsonObject obj) {
        List<StatisticCondition> conditions = new ArrayList<>();
        if (obj.has("conditions")) for (JsonElement element : obj.getAsJsonArray("conditions")) {
            JsonObject condition = element.getAsJsonObject();
            JsonObject stat = GsonHelper.getAsJsonObject(condition, "statistic");
            Optional<ResourceLocation> tag = stat.has("tag") ? Optional.of(ResourceLocation.parse(stat.get("tag").getAsString())) : Optional.empty();
            List<ResourceLocation> values = stat.has("ids") ? ids(stat.getAsJsonArray("ids")).stream()
                    .sorted(Comparator.comparing(Object::toString)).toList() : List.of();
            StatisticSelector selector = new StatisticSelector(ResourceLocation.parse(GsonHelper.getAsString(stat, "provider", "ltsxlogica:vanilla")),
                    ResourceLocation.parse(GsonHelper.getAsString(stat, "type")), GsonHelper.getAsBoolean(stat, "all", false), values, tag);
            conditions.add(new StatisticCondition(selector, ComparisonOperator.parse(GsonHelper.getAsString(condition, "operator")), threshold(condition.get("value"))));
        }
        String mode = GsonHelper.getAsString(obj, "match", "all");
        if (!mode.equals("all") && !mode.equals("any")) throw new IllegalArgumentException("match must be all or any");
        return new MasteryStage(level, mode.equals("any"), conditions);
    }

    private static NumericThreshold threshold(JsonElement element) {
        if (element == null) throw new IllegalArgumentException("Missing numeric value");
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return NumericThreshold.constant(element.getAsBigDecimal());
        JsonObject value = element.getAsJsonObject();
        return new NumericThreshold(decimal(value, "base", BigDecimal.ZERO), decimal(value, "per_level", BigDecimal.ZERO),
                integer(value, "level_offset", 0), integer(value, "power", 1));
    }

    private static int integer(JsonObject obj, String key, int fallback) {
        if (!obj.has(key)) return fallback;
        JsonElement value = obj.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be an integer");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException(key + " must be an integer within int range", ex); }
    }

    private static BigDecimal decimal(JsonObject obj, String key, BigDecimal fallback) {
        if (!obj.has(key)) return fallback;
        JsonElement value = obj.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be numeric");
        return value.getAsBigDecimal();
    }

    private static RuneDefinition rune(ResourceLocation id, JsonObject obj) {
        return new RuneDefinition(id, GsonHelper.getAsString(obj, "name", id.toString()), ids(GsonHelper.getAsJsonArray(obj, "series")),
                integer(obj, "required_level", -1), ResourceLocation.parse(GsonHelper.getAsString(obj, "enchantment")),
                integer(obj, "enchantment_level", -1));
    }

    private static Set<ResourceLocation> ids(JsonArray array) {
        Set<ResourceLocation> values = new HashSet<>();
        if (array.size() > 256) throw new IllegalArgumentException("Too many IDs");
        for (JsonElement value : array) if (!values.add(ResourceLocation.parse(value.getAsString()))) throw new IllegalArgumentException("Duplicate ID");
        return values;
    }
}
