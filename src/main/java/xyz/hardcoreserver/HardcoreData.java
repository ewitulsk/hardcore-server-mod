package xyz.hardcoreserver;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent world state: who is dead, who has been bought back, prices, and where the shrines are. */
public class HardcoreData extends SavedData {
    private record DeadEntry(UUID id, String name) {
        static final Codec<DeadEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(DeadEntry::id),
                Codec.STRING.fieldOf("name").forGetter(DeadEntry::name)
        ).apply(i, DeadEntry::new));
    }

    private record ReviveEntry(UUID id, Optional<GlobalPos> dest) {
        static final Codec<ReviveEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(ReviveEntry::id),
                GlobalPos.CODEC.optionalFieldOf("dest").forGetter(ReviveEntry::dest)
        ).apply(i, ReviveEntry::new));
    }

    private static final Codec<HardcoreData> CODEC = RecordCodecBuilder.create(i -> i.group(
            DeadEntry.CODEC.listOf().optionalFieldOf("dead", List.of()).forGetter(d -> d.dead.entrySet().stream()
                    .map(e -> new DeadEntry(e.getKey(), e.getValue())).toList()),
            ReviveEntry.CODEC.listOf().optionalFieldOf("pending_revives", List.of()).forGetter(d -> d.pendingRevives.entrySet().stream()
                    .map(e -> new ReviveEntry(e.getKey(), e.getValue())).toList()),
            GlobalPos.CODEC.listOf().optionalFieldOf("shrines", List.of()).forGetter(d -> d.shrines),
            GlobalPos.CODEC.listOf().optionalFieldOf("pending_villages", List.of()).forGetter(d -> d.pendingVillages),
            Codec.INT.optionalFieldOf("revives_purchased", 0).forGetter(d -> d.revivesPurchased)
    ).apply(i, HardcoreData::new));

    private static final SavedDataType<HardcoreData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(HardcoreServer.MOD_ID, "state"), HardcoreData::new, CODEC);

    /** Dead players (uuid -> last known name). */
    private final Map<UUID, String> dead = new LinkedHashMap<>();
    /** Players who have been bought back but not yet brought back to life (uuid -> where to put them, may be absent). */
    private final Map<UUID, Optional<GlobalPos>> pendingRevives = new LinkedHashMap<>();
    /** Respawn anchor positions of registered shrines. */
    private final List<GlobalPos> shrines = new ArrayList<>();
    /** Village centers that still need a shrine built once the area is loaded. */
    private final List<GlobalPos> pendingVillages = new ArrayList<>();
    /** How many buy-backs have been paid for on this world; drives the doubling price. */
    private int revivesPurchased;

    public HardcoreData() {}

    private HardcoreData(List<DeadEntry> dead, List<ReviveEntry> revives, List<GlobalPos> shrines,
                         List<GlobalPos> pendingVillages, int revivesPurchased) {
        dead.forEach(e -> this.dead.put(e.id(), e.name()));
        revives.forEach(e -> this.pendingRevives.put(e.id(), e.dest()));
        this.shrines.addAll(shrines);
        this.pendingVillages.addAll(pendingVillages);
        this.revivesPurchased = revivesPurchased;
    }

    public static HardcoreData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---- dead players ----

    public Map<UUID, String> dead() {
        return dead;
    }

    public boolean isDead(UUID id) {
        return dead.containsKey(id);
    }

    public void markDead(UUID id, String name) {
        dead.put(id, name);
        setDirty();
    }

    // ---- revives ----

    public Map<UUID, Optional<GlobalPos>> pendingRevives() {
        return pendingRevives;
    }

    public boolean isRevivePending(UUID id) {
        return pendingRevives.containsKey(id);
    }

    public void queueRevive(UUID id, Optional<GlobalPos> destination) {
        pendingRevives.put(id, destination);
        setDirty();
    }

    // ---- pricing ----

    public int revivesPurchased() {
        return revivesPurchased;
    }

    /** Diamonds needed for the next buy-back: base, then doubling each time (5, 10, 20, 40, ...). */
    public int currentReviveCost() {
        return costForPurchase(revivesPurchased);
    }

    /** Cost of the purchase with the given zero-based index, capped so it never overflows. */
    public static int costForPurchase(int index) {
        long cost = Config.BASE_REVIVE_COST.get();
        for (int i = 0; i < index && cost < Integer.MAX_VALUE; i++) {
            cost *= 2;
        }
        return (int) Math.min(cost, Integer.MAX_VALUE);
    }

    public void recordPurchase() {
        revivesPurchased++;
        setDirty();
    }

    public void setRevivesPurchased(int count) {
        revivesPurchased = Math.max(0, count);
        setDirty();
    }

    // ---- shrines ----

    public List<GlobalPos> shrines() {
        return shrines;
    }

    public void addShrine(GlobalPos pos) {
        if (!shrines.contains(pos)) {
            shrines.add(pos);
            setDirty();
        }
    }

    public boolean removeShrine(GlobalPos pos) {
        boolean removed = shrines.remove(pos);
        if (removed) setDirty();
        return removed;
    }

    public List<GlobalPos> pendingVillages() {
        return pendingVillages;
    }

    public void addPendingVillage(GlobalPos pos) {
        if (!pendingVillages.contains(pos)) {
            pendingVillages.add(pos);
            setDirty();
        }
    }
}
