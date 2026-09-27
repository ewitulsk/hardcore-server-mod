package xyz.hardcoreserver;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue FORCE_HARDCORE;
    public static final ModConfigSpec.BooleanValue DISABLE_NATURAL_REGEN;
    public static final ModConfigSpec.DoubleValue FOOD_HEAL_PER_HUNGER_POINT;
    public static final ModConfigSpec.IntValue BASE_REVIVE_COST;
    public static final ModConfigSpec.BooleanValue GENERATE_VILLAGE_SHRINES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("hardcore");
        FORCE_HARDCORE = b
                .comment("Force the world into hardcore mode (hardcore hearts, difficulty locked to Hard).",
                        "Players who die are always put into spectator mode until revived.",
                        "Requires a server restart to change.")
                .define("forceHardcore", true);
        b.pop();

        b.push("health");
        DISABLE_NATURAL_REGEN = b
                .comment("Disable natural (saturation-based) health regeneration via the naturalRegeneration gamerule.")
                .define("disableNaturalRegen", true);
        FOOD_HEAL_PER_HUNGER_POINT = b
                .comment("Health restored (in half-hearts) per hunger point of food eaten. E.g. steak = 8 hunger points.",
                        "Set to 0 to disable healing from food.")
                .defineInRange("foodHealPerHungerPoint", 1.0, 0.0, 20.0);
        b.pop();

        b.push("respawn");
        BASE_REVIVE_COST = b
                .comment("Diamonds needed for the FIRST buy-back on the server. Every buy-back after that",
                        "costs double the previous one: 5, 10, 20, 40, 80, ... (shared by everyone on the server).")
                .defineInRange("baseReviveCostDiamonds", 5, 1, 1_000_000);
        GENERATE_VILLAGE_SHRINES = b
                .comment("Automatically build a Respawn Shrine in newly generated villages.")
                .define("generateVillageShrines", true);
        b.pop();

        SPEC = b.build();
    }

    /** Safe to call before the config has loaded (e.g. very early world loading). */
    public static boolean forceHardcoreSafe() {
        try {
            return !SPEC.isLoaded() || FORCE_HARDCORE.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private Config() {}
}
