package link.botwmcs.ltsxlogica.mastery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import link.botwmcs.ltsxlogica.api.mastery.*;
import link.botwmcs.ltsxlogica.data.mastery.MasteryDefinitionParser;
import link.botwmcs.ltsxlogica.data.persistence.mastery.PlayerMasteryData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Dedicated regression source set, run by masteryTest rather than a JUnit discovery task. */
public final class MasterySystemTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        comparisons();
        definitions();
        persistence();
        synchronization();
        System.out.println("Mastery regression passed: " + assertions + " assertions");
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) {
        assertions++;
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(message);
    }
    private static void comparisons() {
        for (ComparisonOperator op : ComparisonOperator.values()) {
            boolean[] expected = switch (op) {
                case GREATER_THAN -> new boolean[]{false, false, true};
                case LESS_THAN -> new boolean[]{true, false, false};
                case GREATER_OR_EQUAL -> new boolean[]{false, true, true};
                case LESS_OR_EQUAL -> new boolean[]{true, true, false};
                case EQUAL -> new boolean[]{false, true, false};
            };
            for (int index = 0; index < 3; index++) check(op.test(9 + index, BigDecimal.TEN) == expected[index], "Comparison " + op);
            check(ComparisonOperator.parse(op.symbol()) == op, "Operator parse");
        }
        check(ComparisonOperator.parse("=") == ComparisonOperator.EQUAL, "Equals alias");
        check(ComparisonOperator.GREATER_THAN.test(2, new BigDecimal("1.999")), "Decimal threshold");
        check(ComparisonOperator.EQUAL.test(Long.MAX_VALUE, BigDecimal.valueOf(Long.MAX_VALUE)), "Exact long comparison");
        rejects(() -> ComparisonOperator.parse("!="), "Unknown operator must reject");
        rejects(() -> new NumericThreshold(BigDecimal.ZERO, BigDecimal.ONE, 0, 9), "Unbounded power");
    }
    private static JsonObject defaults() throws Exception {
        try (var stream = MasterySystemTest.class.getResourceAsStream("/data/ltsxlogica/ltsxlogica/mastery/default.json")) {
            if (stream == null) throw new AssertionError("Missing default datapack resource");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void definitions() throws Exception {
        JsonObject defaults = defaults();
        var defs = MasteryDefinitionParser.parse(Map.of(id("ltsxlogica:default"), defaults));
        check(defs.masteries().size() == 4, "Four registered masteries");
        check(defs.runes().size() == 1, "Only Efficiency I registered");
        MasteryDefinition miners = defs.masteries().get(id("ltsxlogica:miners"));
        check(miners.stages().size() == 99, "99 explicit compiled stages");
        check(miners.advance(1, s -> 24) == 1, "Below level 2 threshold");
        check(miners.advance(1, s -> 25) == 2, "Exact level 2 threshold");
        check(miners.advance(1, s -> 100) == 3, "Catch-up all intermediate stages");
        check(miners.advance(1, s -> 225) == 4, "Three awarded slots");
        check(miners.advance(1, s -> 245025) == 100, "Level cap");
        check(miners.advance(50, s -> 0) == 50, "No ordinary downgrade");
        check(miners.stages().get(51).conditions().getFirst().value().atLevel(51).equals(new BigDecimal("62500")), "Dynamic target-level threshold");
        check(defs.runes().get(id("ltsxlogica:efficiency_i")).requiredLevel() == 2, "Rune has a level gate");
        JsonObject override = JsonParser.parseString("{\"runes\":{\"ltsxlogica:efficiency_i\":{\"enabled\":false}}}").getAsJsonObject();
        Map<ResourceLocation, com.google.gson.JsonElement> bundles = new HashMap<>();
        bundles.put(id("aaa:override"), override); bundles.put(id("ltsxlogica:default"), defaults);
        check(MasteryDefinitionParser.parse(bundles).runes().isEmpty(), "Default loads first even before lexical override");
        JsonObject bad = defaults.deepCopy();
        bad.getAsJsonObject("masteries").getAsJsonObject("ltsxlogica:miners").getAsJsonArray("stages").get(0).getAsJsonObject().addProperty("from", 3);
        rejects(() -> MasteryDefinitionParser.parse(Map.of(id("ltsxlogica:default"), bad)), "Stage gaps reject atomically");
        JsonObject overlap = defaults.deepCopy();
        var stages = overlap.getAsJsonObject("masteries").getAsJsonObject("ltsxlogica:miners").getAsJsonArray("stages");
        stages.add(stages.get(0).deepCopy());
        rejects(() -> MasteryDefinitionParser.parse(Map.of(id("ltsxlogica:default"), overlap)), "Stage overlaps reject");
        JsonObject fractional = defaults.deepCopy();
        fractional.getAsJsonObject("runes").getAsJsonObject("ltsxlogica:efficiency_i").addProperty("required_level", 2.5);
        rejects(() -> MasteryDefinitionParser.parse(Map.of(id("ltsxlogica:default"), fractional)), "Fractional level must not silently truncate");
        JsonObject conjunction = JsonParser.parseString("""
            {"masteries":{"test:gate":{"name":"Gate","allowed_series":["test:x"],"stages":[
              {"level":2,"conditions":[
                {"statistic":{"type":"minecraft:mined","all":true},"operator":">=","value":10},
                {"statistic":{"type":"minecraft:custom","ids":["minecraft:fish_caught"]},"operator":"==","value":2}
              ]}]}}}
            """).getAsJsonObject();
        var gate = MasteryDefinitionParser.parse(Map.of(id("test:gate"), conjunction)).masteries().get(id("test:gate"));
        check(gate.advance(1, s -> s.all() ? 12 : 1) == 1, "All conditions required");
        check(gate.advance(1, s -> s.all() ? 12 : 2) == 2, "Exact equality stage unlock");
        check(gate.advance(1, s -> s.all() ? 12 : 3) == 1, "Equality does not silently become >=");
        conjunction.getAsJsonObject("masteries").getAsJsonObject("test:gate").getAsJsonArray("stages").get(0).getAsJsonObject().addProperty("match", "any");
        var anyGate = MasteryDefinitionParser.parse(Map.of(id("test:gate"), conjunction)).masteries().get(id("test:gate"));
        check(anyGate.advance(1, s -> s.all() ? 12 : 1) == 2, "Any condition can unlock OR stage");
    }
    private static void persistence() {
        CompoundTag original = new CompoundTag(); original.putString("extension", "preserve");
        PlayerMasteryData data = new PlayerMasteryData(original);
        PlayerMasteryData.Progress miners = new PlayerMasteryData.Progress();
        miners.level = 4; miners.activated.add(0);
        RuneInstance rune = new RuneInstance(id("ltsxlogica:efficiency_i"), UUID.randomUUID());
        miners.bindings.put(0, rune); data.masteries.put(id("ltsxlogica:miners"), miners); data.revision = 7;
        CompoundTag saved = data.save();
        PlayerMasteryData restored = new PlayerMasteryData(saved);
        check(restored.revision == 7, "Revision persisted");
        check(restored.masteries.get(id("ltsxlogica:miners")).level == 4, "Level persisted");
        check(restored.masteries.get(id("ltsxlogica:miners")).snapshot().unlockedSlots() == 3, "Stable implicit slot rewards");
        check(restored.masteries.get(id("ltsxlogica:miners")).bindings.get(0).equals(rune), "Complete binding identity persisted");
        check(restored.save().getString("extension").equals("preserve"), "Unknown root fields preserved");
        restored.masteries.get(id("ltsxlogica:miners")).bindings.remove(0);
        check(new PlayerMasteryData(restored.save()).masteries.get(id("ltsxlogica:miners")).bindings.isEmpty(), "Unbinding persists");
        check(new PlayerMasteryData(saved.copy()).masteries.size() == 1, "Clone/roundtrip does not duplicate records");
        saved.putInt("schema_version", 99);
        rejects(() -> new PlayerMasteryData(saved), "Future schema cannot be overwritten");
        rejects(() -> restored.masteries.get(id("ltsxlogica:miners")).snapshot().activatedSlots().add(2), "Snapshots immutable");
    }
    private static void synchronization() {
        String value = "x".repeat(8191) + "\ud83d\ude80" + "符文";
        var chunks = link.botwmcs.ltsxlogica.mastery.network.MasterySyncPayload.split(value);
        check(chunks.size() == 2, "Unicode boundary needs two chunks");
        check(String.join("", chunks).equals(value), "Unicode snapshot roundtrip");
        check(chunks.stream().allMatch(s -> s.length() <= 8192 && !Character.isHighSurrogate(s.charAt(s.length() - 1))), "Chunks preserve surrogate pairs");
        rejects(() -> link.botwmcs.ltsxlogica.mastery.network.MasterySyncPayload.split("x".repeat(8192 * 128 + 1)), "Excessive snapshots rejected");
    }
}
