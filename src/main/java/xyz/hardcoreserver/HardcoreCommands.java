package xyz.hardcoreserver;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /hardcore dead                 - list dead players
 * /hardcore shrines              - list the nearest Respawn Shrines
 * /hardcore revive &lt;player&gt;      - (op) revive a dead player for free
 * /hardcore shrine create        - (op) build a shrine where you stand
 * /hardcore shrine remove        - (op) unregister the nearest shrine within 8 blocks
 */
public final class HardcoreCommands {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("hardcore")
                .then(Commands.literal("dead").executes(HardcoreCommands::listDead))
                .then(Commands.literal("shrines").executes(HardcoreCommands::listShrines))
                .then(Commands.literal("revive").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        HardcoreData.get(ctx.getSource().getServer()).dead().values(), b))
                                .executes(HardcoreCommands::revive)))
                .then(Commands.literal("shrine").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("create").executes(HardcoreCommands::createShrine))
                        .then(Commands.literal("remove").executes(HardcoreCommands::removeShrine))));
    }

    private static int listDead(CommandContext<CommandSourceStack> ctx) {
        HardcoreData data = HardcoreData.get(ctx.getSource().getServer());
        if (data.dead().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Nobody is dead.").withStyle(ChatFormatting.GREEN), false);
            return 0;
        }
        List<String> names = new ArrayList<>();
        data.dead().forEach((id, name) -> names.add(data.isRevivePending(id) ? name + " (revive pending)" : name));
        ctx.getSource().sendSuccess(() -> Component.literal("Dead players (" + names.size() + "): " + String.join(", ", names))
                .withStyle(ChatFormatting.RED), false);
        return names.size();
    }

    private static int listShrines(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        HardcoreData data = HardcoreData.get(src.getServer());
        Vec3 here = src.getPosition();
        List<GlobalPos> list = new ArrayList<>(data.shrines());
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No Respawn Shrines exist yet. They appear in newly generated villages.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        list.sort(Comparator.comparingDouble((GlobalPos g) -> g.dimension().equals(src.getLevel().dimension()) ? 0 : 1)
                .thenComparingDouble(g -> g.pos().distToCenterSqr(here)));
        src.sendSuccess(() -> Component.literal("Respawn Shrines (revive cost: " + Config.REVIVE_COST.get() + " diamonds):")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (GlobalPos g : list.subList(0, Math.min(5, list.size()))) {
            BlockPos p = g.pos();
            boolean sameDim = g.dimension().equals(src.getLevel().dimension());
            String dist = sameDim ? " - " + (int) Math.sqrt(p.distToCenterSqr(here)) + " blocks away" : "";
            src.sendSuccess(() -> Component.literal("  " + p.getX() + ", " + p.getY() + ", " + p.getZ()
                    + " (" + g.dimension().location().getPath() + ")" + dist).withStyle(ChatFormatting.WHITE), false);
        }
        return list.size();
    }

    private static int revive(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "player");
        HardcoreData data = HardcoreData.get(src.getServer());
        UUID target = null;
        for (Map.Entry<UUID, String> e : data.dead().entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) target = e.getKey();
        }
        if (target == null) {
            src.sendFailure(Component.literal(name + " is not dead."));
            return 0;
        }
        Optional<GlobalPos> dest = src.getEntity() instanceof ServerPlayer p
                ? Optional.of(GlobalPos.of(p.level().dimension(), p.blockPosition())) : Optional.empty();
        data.queueRevive(target, dest);
        String finalName = data.dead().get(target);
        src.sendSuccess(() -> Component.literal("Reviving " + finalName + ".").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int createShrine(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos ground = BlockPos.containing(src.getPosition()).below();
        BlockPos anchor = ShrineBuilder.build(level, ground);
        src.sendSuccess(() -> Component.literal("Built Respawn Shrine at " + anchor.toShortString()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeShrine(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        HardcoreData data = HardcoreData.get(src.getServer());
        GlobalPos nearest = ShrineEvents.nearestShrine(data, level.dimension(), BlockPos.containing(src.getPosition()), 8);
        if (nearest == null) {
            src.sendFailure(Component.literal("No Respawn Shrine within 8 blocks."));
            return 0;
        }
        data.removeShrine(nearest);
        ShrineBuilder.removeLabel(level, nearest.pos());
        src.sendSuccess(() -> Component.literal("Unregistered Respawn Shrine at " + nearest.pos().toShortString()
                + ". The blocks are now ordinary blocks.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private HardcoreCommands() {}
}
