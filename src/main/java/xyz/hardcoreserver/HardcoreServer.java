package xyz.hardcoreserver;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;

public class HardcoreServer implements ModInitializer {
    public static final String MOD_ID = "hardcoreserver";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        Config.load();

        ServerLifecycleEvents.SERVER_STARTED.register(HardcoreEvents::onServerStarted);
        ServerLivingEntityEvents.AFTER_DEATH.register(HardcoreEvents::onDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(HardcoreEvents::onRespawn);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> HardcoreEvents.onLogin(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(HardcoreEvents::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(ShrineEvents::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(FortressCompass::tick);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 60 == 0) Config.reloadIfChanged();
        });

        ServerChunkEvents.CHUNK_LOAD.register(ShrineEvents::onChunkLoad);
        UseBlockCallback.EVENT.register(ShrineEvents::onUseBlock);
        PlayerBlockBreakEvents.BEFORE.register(ShrineEvents::onBreak);

        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> HardcoreCommands.register(dispatcher));
        LOGGER.info("Hardcore Server loaded");
    }
}
