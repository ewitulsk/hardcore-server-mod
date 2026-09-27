package xyz.hardcoreserver;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(HardcoreServer.MOD_ID)
public class HardcoreServer {
    public static final String MOD_ID = "hardcoreserver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HardcoreServer(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.register(HardcoreEvents.class);
        NeoForge.EVENT_BUS.register(ShrineEvents.class);
        NeoForge.EVENT_BUS.addListener(HardcoreCommands::register);
    }
}
