package xyz.hardcoreserver;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

/** Simple properties-file config at config/hardcoreserver.properties. */
public final class Config {
    public static final class Value<T> {
        private final String key;
        private final String comment;
        private final T def;
        private final Function<String, T> parser;
        private T value;

        private Value(String key, T def, Function<String, T> parser, String comment) {
            this.key = key;
            this.def = def;
            this.parser = parser;
            this.comment = comment;
            this.value = def;
        }

        public T get() {
            return value;
        }
    }

    private static final List<Value<?>> ALL = new ArrayList<>();

    public static final Value<Boolean> FORCE_HARDCORE = bool("forceHardcore", true,
            "Force the world into hardcore mode (hardcore hearts, difficulty locked to Hard). Dead players become spectators.");
    public static final Value<Boolean> DISABLE_NATURAL_REGEN = bool("disableNaturalRegen", true,
            "Disable natural (saturation-based) health regeneration via the natural_health_regeneration gamerule.");
    public static final Value<Double> FOOD_HEAL_PER_HUNGER_POINT = dbl("foodHealPerHungerPoint", 1.0,
            "Health restored (in half-hearts) per hunger point of food eaten. Steak = 8 hunger points. 0 disables.");
    public static final Value<Integer> BASE_REVIVE_COST = integer("baseReviveCostDiamonds", 5,
            "Diamonds for the FIRST buy-back on the server. Each later buy-back costs double: 5, 10, 20, 40, 80, ...");
    public static final Value<Boolean> GENERATE_VILLAGE_SHRINES = bool("generateVillageShrines", true,
            "Automatically build a Respawn Shrine in newly generated villages.");

    private static Value<Boolean> bool(String k, boolean d, String c) {
        return add(new Value<>(k, d, Boolean::parseBoolean, c));
    }

    private static Value<Integer> integer(String k, int d, String c) {
        return add(new Value<>(k, d, s -> Math.max(1, Integer.parseInt(s)), c));
    }

    private static Value<Double> dbl(String k, double d, String c) {
        return add(new Value<>(k, d, s -> Math.max(0.0, Double.parseDouble(s)), c));
    }

    private static <T> Value<T> add(Value<T> v) {
        ALL.add(v);
        return v;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(HardcoreServer.MOD_ID + ".properties");
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                props.load(r);
            } catch (IOException e) {
                HardcoreServer.LOGGER.error("Could not read {}", file, e);
            }
        }
        for (Value<?> v : ALL) parse(v, props.getProperty(v.key));
        // Rewrite the file so new options / comments always appear.
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                w.write("# Hardcore Server config. Restart the server after editing.\n");
                for (Value<?> v : ALL) {
                    w.write("\n# " + v.comment + "\n# Default: " + v.def + "\n" + v.key + "=" + v.value + "\n");
                }
            }
        } catch (IOException e) {
            HardcoreServer.LOGGER.error("Could not write {}", file, e);
        }
    }

    private static <T> void parse(Value<T> v, String raw) {
        if (raw == null) return;
        try {
            v.value = v.parser.apply(raw.trim());
        } catch (RuntimeException e) {
            HardcoreServer.LOGGER.warn("Invalid value '{}' for {}, using default {}", raw, v.key, v.def);
        }
    }

    /** Used by the mixin, which can run very early. */
    public static boolean forceHardcoreSafe() {
        return FORCE_HARDCORE.get();
    }

    private Config() {}
}
