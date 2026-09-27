package xyz.hardcoreserver;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Hardcore death handling and the "no natural regen, food heals" health rules. */
public final class HardcoreEvents {

    public static void onServerStarted(MinecraftServer server) {
        enforceGameRules(server);
    }

    private static void enforceGameRules(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        if (Config.DISABLE_NATURAL_REGEN.get() && rules.get(GameRules.NATURAL_HEALTH_REGENERATION)) {
            rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            HardcoreServer.LOGGER.info("Hardcore Server: natural regeneration disabled");
        }
    }

    // ---------------------------------------------------------------- death -> spectator

    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer player) {
            HardcoreData.get(player.level().getServer()).markDead(player.getUUID(), player.getGameProfile().name());
        }
    }

    public static void onRespawn(ServerPlayer oldPlayer, ServerPlayer player, boolean alive) {
        if (alive) return; // returning from the End, not a death
        HardcoreData data = HardcoreData.get(player.level().getServer());
        data.markDead(player.getUUID(), player.getGameProfile().name());
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.literal("You died! You are now a spectator until another player buys you back with diamonds at a village Respawn Shrine.")
                .withStyle(ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("Use /hardcore shrines to see where the shrines are.")
                .withStyle(ChatFormatting.GRAY));
    }

    public static void onLogin(ServerPlayer player) {
        HardcoreData data = HardcoreData.get(player.level().getServer());
        if (data.isDead(player.getUUID())) {
            // Keep the stored name fresh in case they renamed.
            data.markDead(player.getUUID(), player.getGameProfile().name());
            if (!player.isDeadOrDying() && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                    && !data.isRevivePending(player.getUUID())) {
                player.setGameMode(GameType.SPECTATOR);
            }
        }
    }

    public static void onServerTick(MinecraftServer server) {
        Reviver.processPending(server);

        if (server.getTickCount() % 100 == 0) {
            enforceGameRules(server);
            // If an operator manually took a dead player out of spectator, treat them as revived.
            HardcoreData data = HardcoreData.get(server);
            Iterator<Map.Entry<UUID, String>> it = data.dead().entrySet().iterator();
            while (it.hasNext()) {
                UUID id = it.next().getKey();
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && !p.isDeadOrDying() && !data.isRevivePending(id)
                        && p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    it.remove();
                    data.setDirty();
                }
            }
        }
    }

    // ---------------------------------------------------------------- food heals (called from mixins)

    public static void healFromFood(Player player, int nutrition) {
        if (!(player instanceof ServerPlayer)) return;
        float amount = (float) (nutrition * Config.FOOD_HEAL_PER_HUNGER_POINT.get());
        if (amount > 0 && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
            player.heal(amount);
        }
    }

    private HardcoreEvents() {}
}
