package xyz.hardcoreserver;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.server.permissions.Permissions;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Village shrine generation, shrine interaction and shrine protection. */
public final class ShrineEvents {
    /** Minimum distance between two auto-generated shrines. */
    private static final int MIN_SHRINE_SPACING = 48;

    private static final Queue<GlobalPos> newVillages = new ConcurrentLinkedQueue<>();

    // ---------------------------------------------------------------- generation

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean newChunk) {
        if (!newChunk) return;
        Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (starts.isEmpty()) return;
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> e : starts.entrySet()) {
            StructureStart start = e.getValue();
            if (!start.isValid() || start.getPieces().isEmpty()) continue;
            if (!registry.wrapAsHolder(e.getKey()).is(StructureTags.VILLAGE)) continue;
            BlockPos center = start.getPieces().get(0).getBoundingBox().getCenter();
            newVillages.add(GlobalPos.of(level.dimension(), center));
        }
    }

    public static void onServerTick(MinecraftServer server) {
        HardcoreData data = HardcoreData.get(server);
        GlobalPos found;
        while ((found = newVillages.poll()) != null) {
            if (Config.GENERATE_VILLAGE_SHRINES.get()) data.addPendingVillage(found);
        }
        if (server.getTickCount() % 20 != 0 || data.pendingVillages().isEmpty()) return;

        Iterator<GlobalPos> it = data.pendingVillages().iterator();
        while (it.hasNext()) {
            GlobalPos village = it.next();
            ServerLevel level = server.getLevel(village.dimension());
            if (level == null || nearestShrine(data, village.dimension(), village.pos(), MIN_SHRINE_SPACING) != null) {
                it.remove();
                data.setDirty();
                continue;
            }
            BlockPos c = village.pos();
            int r = ShrineBuilder.REQUIRED_RADIUS;
            if (!level.hasChunksAt(c.getX() - r, c.getZ() - r, c.getX() + r, c.getZ() + r)) continue;
            it.remove();
            data.setDirty();
            try {
                ShrineBuilder.buildNear(level, c);
            } catch (Exception ex) {
                HardcoreServer.LOGGER.error("Failed to build Respawn Shrine near {}", c, ex);
            }
        }
    }

    // ---------------------------------------------------------------- interaction

    public static InteractionResult onUseBlock(Player p, net.minecraft.world.level.Level lvl, InteractionHand hand, BlockHitResult hit) {
        if (!(lvl instanceof ServerLevel level) || !(p instanceof ServerPlayer player)) return InteractionResult.PASS;
        GlobalPos pos = GlobalPos.of(level.dimension(), hit.getBlockPos());
        HardcoreData data = HardcoreData.get(level.getServer());
        if (!data.shrines().contains(pos)) return InteractionResult.PASS;
        if (!level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
            data.removeShrine(pos); // shrine was replaced somehow
            return InteractionResult.PASS;
        }
        // Never let the anchor behave like a vanilla anchor (charging, exploding, setting spawn).
        if (hand == InteractionHand.MAIN_HAND && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            ReviveMenu.open(player, level, hit.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- protection

    /** Returns false to cancel the break. */
    public static boolean onBreak(net.minecraft.world.level.Level lvl, Player player, BlockPos bpos, BlockState state, BlockEntity be) {
        if (!(lvl instanceof ServerLevel level)) return true;
        HardcoreData data = HardcoreData.get(level.getServer());
        GlobalPos anchor = protectingShrine(data, level.dimension(), bpos);
        if (anchor == null) return true;
        if (player.isCreative() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            if (anchor.pos().equals(bpos)) {
                data.removeShrine(anchor);
                ShrineBuilder.removeLabel(level, anchor.pos());
                player.sendSystemMessage(Component.literal("Respawn Shrine removed.").withStyle(ChatFormatting.YELLOW));
            }
            return true;
        }
        player.sendSystemMessage(Component.literal("The Respawn Shrine is protected.").withStyle(ChatFormatting.RED));
        return false;
    }

    /** Called from the explosion mixin: drops shrine blocks from an explosion's block list. */
    public static List<BlockPos> filterExplosion(ServerLevel level, List<BlockPos> blocks) {
        HardcoreData data = HardcoreData.get(level.getServer());
        if (data.shrines().isEmpty()) return blocks;
        blocks.removeIf(p -> protectingShrine(data, level.dimension(), p) != null);
        return blocks;
    }

    /** The shrine whose 3x3x3 footprint contains {@code pos}, or null. */
    private static GlobalPos protectingShrine(HardcoreData data, net.minecraft.resources.ResourceKey<Level> dim, BlockPos pos) {
        for (GlobalPos s : data.shrines()) {
            if (!s.dimension().equals(dim)) continue;
            BlockPos a = s.pos();
            if (Math.abs(pos.getX() - a.getX()) <= 1 && Math.abs(pos.getZ() - a.getZ()) <= 1
                    && pos.getY() - a.getY() >= -1 && pos.getY() - a.getY() <= 1) {
                return s;
            }
        }
        return null;
    }

    /** Nearest shrine in {@code dim} within {@code maxDist} blocks (horizontal), or null. */
    public static GlobalPos nearestShrine(HardcoreData data, net.minecraft.resources.ResourceKey<Level> dim, BlockPos pos, double maxDist) {
        GlobalPos best = null;
        double bestSq = maxDist * maxDist;
        for (GlobalPos s : data.shrines()) {
            if (!s.dimension().equals(dim)) continue;
            double dx = s.pos().getX() - pos.getX(), dz = s.pos().getZ() - pos.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bestSq) {
                bestSq = d;
                best = s;
            }
        }
        return best;
    }

    private ShrineEvents() {}
}
