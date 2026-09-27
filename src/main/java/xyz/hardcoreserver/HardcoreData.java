package xyz.hardcoreserver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent world state: who is dead, who has been bought back, and where the shrines are. */
public class HardcoreData extends SavedData {
    private static final String NAME = HardcoreServer.MOD_ID;

    /** Dead players (uuid -> last known name). */
    private final Map<UUID, String> dead = new LinkedHashMap<>();
    /** Players who have been bought back but not yet brought back to life (uuid -> where to put them, may be absent). */
    private final Map<UUID, Optional<GlobalPos>> pendingRevives = new LinkedHashMap<>();
    /** Respawn anchor positions of registered shrines. */
    private final List<GlobalPos> shrines = new ArrayList<>();
    /** Village centers that still need a shrine built once the area is loaded. */
    private final List<GlobalPos> pendingVillages = new ArrayList<>();

    public static HardcoreData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HardcoreData::new, HardcoreData::load), NAME);
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

    public void clearDead(UUID id) {
        if (dead.remove(id) != null) setDirty();
        if (pendingRevives.remove(id) != null) setDirty();
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

    // ---- serialization ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag deadList = new ListTag();
        dead.forEach((id, name) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", id);
            e.putString("name", name);
            deadList.add(e);
        });
        tag.put("dead", deadList);

        ListTag reviveList = new ListTag();
        pendingRevives.forEach((id, dest) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", id);
            dest.ifPresent(p -> e.put("dest", writePos(p)));
            reviveList.add(e);
        });
        tag.put("pendingRevives", reviveList);

        tag.put("shrines", writePosList(shrines));
        tag.put("pendingVillages", writePosList(pendingVillages));
        return tag;
    }

    private static HardcoreData load(CompoundTag tag, HolderLookup.Provider registries) {
        HardcoreData data = new HardcoreData();
        for (Tag t : tag.getList("dead", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.dead.put(e.getUUID("id"), e.getString("name"));
        }
        for (Tag t : tag.getList("pendingRevives", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            Optional<GlobalPos> dest = e.contains("dest", Tag.TAG_COMPOUND)
                    ? Optional.of(readPos(e.getCompound("dest"))) : Optional.empty();
            data.pendingRevives.put(e.getUUID("id"), dest);
        }
        data.shrines.addAll(readPosList(tag.getList("shrines", Tag.TAG_COMPOUND)));
        data.pendingVillages.addAll(readPosList(tag.getList("pendingVillages", Tag.TAG_COMPOUND)));
        return data;
    }

    private static CompoundTag writePos(GlobalPos pos) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", pos.dimension().location().toString());
        t.putLong("pos", pos.pos().asLong());
        return t;
    }

    private static GlobalPos readPos(CompoundTag t) {
        ResourceKey<net.minecraft.world.level.Level> dim =
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim")));
        return GlobalPos.of(dim, BlockPos.of(t.getLong("pos")));
    }

    private static ListTag writePosList(List<GlobalPos> list) {
        ListTag out = new ListTag();
        list.forEach(p -> out.add(writePos(p)));
        return out;
    }

    private static List<GlobalPos> readPosList(ListTag list) {
        List<GlobalPos> out = new ArrayList<>();
        for (Tag t : list) out.add(readPos((CompoundTag) t));
        return out;
    }
}
