package xyz.hardcoreserver;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Fortress Compass: a vanilla compass with a hidden tag. The server drives its needle through the vanilla
 * lodestone-tracker component, so unmodded clients render it. In the Nether it points at a fortress - but never the
 * closest one (it picks the second closest). Anywhere else, or while disabled, it spins.
 */
public final class FortressCompass {
    public static final String TAG = "hardcoreserver";
    public static final String TAG_VALUE = "fortress_compass";

    /** How many structure regions around the player to consider (each is ~27 chunks / 432 blocks wide). */
    private static final int REGION_RADIUS = 4;
    /** Max uncached regions to evaluate per player per update, to keep ticks cheap. */
    private static final int REGION_BUDGET = 12;

    /** Nether region (x,z) -> fortress position in that region, if any. Deterministic per seed, so cached forever. */
    private static final Map<Long, Optional<BlockPos>> REGION_CACHE = new HashMap<>();
    private static long cachedSeed = Long.MIN_VALUE;
    /** Last complete answer per player, used while a new area is still being scanned. */
    private static final Map<java.util.UUID, LastTarget> LAST_TARGET = new HashMap<>();

    /** A finished answer and the structure region it was computed around. */
    private record LastTarget(long region, Optional<GlobalPos> target) {}

    public static boolean isFortressCompass(ItemStack stack) {
        if (!stack.is(Items.COMPASS)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && TAG_VALUE.equals(data.copyTag().getStringOr(TAG, ""));
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, TAG_VALUE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Fortress Compass")
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.empty(), false));
        stack.set(DataComponents.LORE, lore(Config.FORTRESS_COMPASS_ENABLED.get()));
        return stack;
    }

    private static ItemLore lore(boolean enabled) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Points toward a Nether fortress...").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
        lines.add(Component.literal("...but never the closest one.").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
        lines.add(Component.literal("Only works in the Nether.").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_RED)));
        if (!enabled) {
            lines.add(Component.literal("DISABLED by the server").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED).withBold(true)));
        }
        return new ItemLore(lines);
    }

    // ---------------------------------------------------------------- per-second update

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        boolean enabled = Config.FORTRESS_COMPASS_ENABLED.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Inventory inv = player.getInventory();
            Optional<GlobalPos> target = null; // computed lazily, once per player
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!isFortressCompass(stack)) continue;
                if (target == null) target = enabled ? findTarget(player) : Optional.empty();
                apply(stack, target, enabled);
            }
        }
    }

    private static void apply(ItemStack stack, Optional<GlobalPos> target, boolean enabled) {
        LodestoneTracker desired = new LodestoneTracker(target, false); // tracked=false: no lodestone required
        if (!desired.equals(stack.get(DataComponents.LODESTONE_TRACKER))) {
            stack.set(DataComponents.LODESTONE_TRACKER, desired);
        }
        ItemLore lore = lore(enabled);
        if (!lore.equals(stack.get(DataComponents.LORE))) {
            stack.set(DataComponents.LORE, lore);
        }
    }

    /** The second-closest fortress to the player, if they are in the Nether and at least two are known nearby. */
    public static Optional<GlobalPos> findTarget(ServerPlayer player) {
        ServerLevel level = player.level();
        if (level.dimension() != Level.NETHER) return Optional.empty();
        Optional<? extends Holder<Structure>> fortress = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(BuiltinStructures.FORTRESS);
        if (fortress.isEmpty()) return Optional.empty();

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        if (state.getLevelSeed() != cachedSeed) {
            REGION_CACHE.clear();
            cachedSeed = state.getLevelSeed();
        }

        List<BlockPos> found = new ArrayList<>();
        int budget = REGION_BUDGET;
        boolean complete = true;
        long centerRegion = Long.MIN_VALUE;
        for (StructurePlacement placement : state.getPlacementsForStructure(fortress.get())) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
            int spacing = spread.spacing();
            int regionX = Math.floorDiv(player.chunkPosition().x(), spacing);
            int regionZ = Math.floorDiv(player.chunkPosition().z(), spacing);
            centerRegion = ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
            for (int dx = -REGION_RADIUS; dx <= REGION_RADIUS; dx++) {
                for (int dz = -REGION_RADIUS; dz <= REGION_RADIUS; dz++) {
                    int rx = regionX + dx, rz = regionZ + dz;
                    long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL) ^ ((long) spacing << 56);
                    Optional<BlockPos> result = REGION_CACHE.get(key);
                    if (result == null) {
                        if (budget-- <= 0) { complete = false; continue; } // finish the rest on later updates
                        result = fortressInRegion(level, state, spread, fortress.get(), rx * spacing, rz * spacing);
                        REGION_CACHE.put(key, result);
                    }
                    result.ifPresent(found::add);
                }
            }
        }

        // Only decide once every region around the player is known; otherwise the "second closest" of a partial
        // scan could actually be the closest fortress. Until then keep the previous answer (or spin).
        // A stale answer from a different area (e.g. after a long teleport) would be wrong, so spin instead.
        if (!complete) {
            LastTarget last = LAST_TARGET.get(player.getUUID());
            return last != null && last.region() == centerRegion ? last.target() : Optional.empty();
        }

        // "Never the closest": sort by distance and skip the nearest one.
        BlockPos here = player.blockPosition();
        found.sort(Comparator.comparingDouble(p -> horizontalDistSqr(p, here)));
        Optional<GlobalPos> target = found.size() < 2 ? Optional.empty() : Optional.of(GlobalPos.of(Level.NETHER, found.get(1)));
        LAST_TARGET.put(player.getUUID(), new LastTarget(centerRegion, target));
        return target;
    }

    private static Optional<BlockPos> fortressInRegion(ServerLevel level, ChunkGeneratorStructureState state,
                                                       RandomSpreadStructurePlacement placement, Holder<Structure> fortress,
                                                       int sourceChunkX, int sourceChunkZ) {
        ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(), sourceChunkX, sourceChunkZ);
        if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) return Optional.empty();
        StructureCheckResult check = level.structureManager().checkStructurePresence(chunk, fortress.value(), placement, false);
        if (check == StructureCheckResult.START_NOT_PRESENT) return Optional.empty();
        if (check == StructureCheckResult.CHUNK_LOAD_NEEDED) {
            StructureStart start = level.structureManager().getStartForStructure(fortress.value(),
                    level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS));
            if (start == null || !start.isValid()) return Optional.empty();
        }
        BlockPos locate = placement.getLocatePos(chunk);
        return Optional.of(new BlockPos(locate.getX(), 64, locate.getZ()));
    }

    private static double horizontalDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** True if this crafting result must be blocked because the compass is disabled. */
    public static boolean blockCrafting(ItemStack result) {
        return !Config.FORTRESS_COMPASS_ENABLED.get() && isFortressCompass(result);
    }

    private FortressCompass() {}

}
