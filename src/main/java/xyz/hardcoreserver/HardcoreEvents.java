package xyz.hardcoreserver;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Hardcore death handling and the "no natural regen, food heals" health rules. */
public final class HardcoreEvents {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        enforceGameRules(event.getServer());
    }

    private static void enforceGameRules(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        if (Config.DISABLE_NATURAL_REGEN.get() && rules.get(GameRules.NATURAL_HEALTH_REGENERATION)) {
            rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            HardcoreServer.LOGGER.info("Hardcore Server: natural regeneration disabled");
        }
    }

    // ---------------------------------------------------------------- death -> spectator

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HardcoreData.get(player.level().getServer()).markDead(player.getUUID(), player.getGameProfile().name());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        HardcoreData data = HardcoreData.get(player.level().getServer());
        data.markDead(player.getUUID(), player.getGameProfile().name());
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.literal("You died! You are now a spectator until another player buys you back with diamonds at a village Respawn Shrine.")
                .withStyle(ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("Use /hardcore shrines to see where the shrines are.")
                .withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
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

    // ---------------------------------------------------------------- food heals

    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food != null) healFromFood(player, food.nutrition());
    }

    @SubscribeEvent
    public static void onEatCake(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        // Cake gives 2 hunger per slice; only counts if the player could actually eat it.
        if (state.getBlock() instanceof CakeBlock && player.canEat(false) && !player.isSecondaryUseActive()
                && player.getMainHandItem().isEmpty()) {
            healFromFood(player, 2);
        }
    }

    private static void healFromFood(ServerPlayer player, int nutrition) {
        float amount = (float) (nutrition * Config.FOOD_HEAL_PER_HUNGER_POINT.get());
        if (amount > 0 && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
            player.heal(amount);
        }
    }

    private HardcoreEvents() {}
}
