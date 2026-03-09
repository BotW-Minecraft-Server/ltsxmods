package link.botwmcs.ltsxlogica;

import java.util.List;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final int SOLAR_TERM_COUNT = 24;
    private static final List<Double> DEFAULT_SOLAR_TERM_BASE_UNITS = List.of(
            -0.25D, -0.15D, -0.10D, 0.00D, 0.00D, 0.05D,
            0.10D, 0.15D, 0.15D, 0.20D, 0.20D, 0.25D,
            0.15D, 0.10D, 0.05D, 0.00D, -0.10D, -0.20D,
            -0.30D, -0.35D, -0.35D, -0.40D, -0.45D, -0.40D
    );

    private static final ModConfigSpec.BooleanValue HEAT_ECLIPTIC_AMBIENT_ENABLED;
    private static final ModConfigSpec.BooleanValue HEAT_ECLIPTIC_PREFER_API_TERM_DELTA;
    private static final ModConfigSpec.DoubleValue HEAT_ECLIPTIC_TERM_UNIT_TO_CELSIUS;
    private static final ModConfigSpec.DoubleValue HEAT_ECLIPTIC_GLOBAL_OFFSET_CELSIUS;
    private static final ModConfigSpec.ConfigValue<List<? extends Double>> HEAT_ECLIPTIC_TERM_BASE_UNITS;

    static final ModConfigSpec SPEC;

    private static volatile boolean heatEclipticAmbientEnabled = true;
    private static volatile boolean heatEclipticPreferApiTermDelta = false;
    private static volatile double heatEclipticTermUnitToCelsius = 14.0D;
    private static volatile double heatEclipticGlobalOffsetCelsius = 0.0D;
    private static volatile double[] heatEclipticTermBaseUnits = toPrimitiveArray(DEFAULT_SOLAR_TERM_BASE_UNITS);

    static {
        BUILDER.push("heat");
        BUILDER.push("ecliptic");

        HEAT_ECLIPTIC_AMBIENT_ENABLED = BUILDER
                .comment("Enable Ecliptic Seasons solar-term temperature integration for heat ambient baseline.")
                .define("ambientEnabled", true);

        HEAT_ECLIPTIC_PREFER_API_TERM_DELTA = BUILDER
                .comment("If true, use Ecliptic API term delta directly instead of this config's 24-term table.")
                .define("preferApiTermDelta", false);

        HEAT_ECLIPTIC_TERM_UNIT_TO_CELSIUS = BUILDER
                .comment("Convert 1.0 solar-term temperature unit into how many Celsius degrees.")
                .defineInRange("termUnitToCelsius", 14.0D, 0.0D, 100.0D);

        HEAT_ECLIPTIC_GLOBAL_OFFSET_CELSIUS = BUILDER
                .comment("Global Celsius offset applied after term conversion.")
                .defineInRange("globalOffsetCelsius", 0.0D, -100.0D, 100.0D);

        HEAT_ECLIPTIC_TERM_BASE_UNITS = BUILDER
                .comment(
                        "24 solar-term baseline values in Ecliptic temperature units.",
                        "Order: BeginningOfSpring, RainWater, InsectsAwakening, SpringEquinox, FreshGreen, GrainRain,",
                        "BeginningOfSummer, LesserFullness, GrainInEar, SummerSolstice, LesserHeat, GreaterHeat,",
                        "BeginningOfAutumn, EndOfHeat, WhiteDew, AutumnalEquinox, ColdDew, FirstFrost,",
                        "BeginningOfWinter, LightSnow, HeavySnow, WinterSolstice, LesserCold, GreaterCold"
                )
                .defineList("termBaseUnits", DEFAULT_SOLAR_TERM_BASE_UNITS, Config::isNumber);

        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private static boolean isNumber(final Object obj) {
        return obj instanceof Number;
    }

    private static double[] toPrimitiveArray(List<Double> list) {
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static void bake() {
        heatEclipticAmbientEnabled = HEAT_ECLIPTIC_AMBIENT_ENABLED.get();
        heatEclipticPreferApiTermDelta = HEAT_ECLIPTIC_PREFER_API_TERM_DELTA.get();
        heatEclipticTermUnitToCelsius = HEAT_ECLIPTIC_TERM_UNIT_TO_CELSIUS.get();
        heatEclipticGlobalOffsetCelsius = HEAT_ECLIPTIC_GLOBAL_OFFSET_CELSIUS.get();

        double[] parsed = toPrimitiveArray(DEFAULT_SOLAR_TERM_BASE_UNITS);
        List<? extends Double> configured = HEAT_ECLIPTIC_TERM_BASE_UNITS.get();
        int bound = Math.min(SOLAR_TERM_COUNT, configured.size());
        for (int i = 0; i < bound; i++) {
            Double value = configured.get(i);
            if (value != null && Double.isFinite(value)) {
                parsed[i] = value.doubleValue();
            }
        }
        heatEclipticTermBaseUnits = parsed;
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            bake();
        }
    }

    public static boolean isHeatEclipticAmbientEnabled() {
        return heatEclipticAmbientEnabled;
    }

    public static boolean isHeatEclipticPreferApiTermDelta() {
        return heatEclipticPreferApiTermDelta;
    }

    public static double getHeatEclipticTermUnitToCelsius() {
        return heatEclipticTermUnitToCelsius;
    }

    public static double getHeatEclipticGlobalOffsetCelsius() {
        return heatEclipticGlobalOffsetCelsius;
    }

    public static double getHeatEclipticTermBaseUnit(int termIndex) {
        if (termIndex < 0 || termIndex >= heatEclipticTermBaseUnits.length) {
            return 0.0D;
        }
        return heatEclipticTermBaseUnits[termIndex];
    }
}

