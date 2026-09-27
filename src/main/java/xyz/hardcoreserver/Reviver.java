package xyz.hardcoreserver;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Brings bought-back players back to life once they are online and past the death screen. */
public final class Reviver {

    /** Called every tick; revives any pending players that can be revived right now. */
    public static void processPending(MinecraftServer server) {
        HardcoreData data = HardcoreData.get(server);
        if (data.pendingRevives().isEmpty()) return;

        Iterator<Map.Entry<UUID, Optional<GlobalPos>>> it = data.pendingRevives().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Optional<GlobalPos>> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            // Offline, or still on the death screen: try again later.
            if (player == null || player.isDeadOrDying()) continue;

            it.remove();
            data.dead().remove(entry.getKey());
            data.setDirty();
            revive(server, player, entry.getValue());
        }
    }

    private static void revive(MinecraftServer server, ServerPlayer player, Optional<GlobalPos> dest) {
        ServerLevel level = null;
        BlockPos pos = null;
        if (dest.isPresent()) {
            level = server.getLevel(dest.get().dimension());
            pos = dest.get().pos();
        }
        if (level == null) {
            // No destination: bring them back to their own respawn point / world spawn.
            level = server.overworld();
            pos = level.getSharedSpawnPos();
            ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
            if (player.getRespawnPosition() != null && respawnLevel != null) {
                level = respawnLevel;
                pos = player.getRespawnPosition().above();
            }
        }

        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.removeAllEffects();
        player.clearFire();
        player.resetFallDistance();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 10, 4));

        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(player.getGameProfile().getName() + " has been brought back to life!")
                        .withStyle(ChatFormatting.GOLD), false);
    }

    private Reviver() {}
}
