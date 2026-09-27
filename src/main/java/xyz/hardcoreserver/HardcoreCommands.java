package xyz.hardcoreserver;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * /hardcore dead                 - list dead players
 * /hardcore shrines              - list the nearest Respawn Shrines
 * /hardcore price                - show the current buy-back price
 * /hardcore price reset          - (op) reset the doubling price back to the base cost
 * /hardcore revive &lt;player&gt;      - (op) revive a dead player for free
 * /hardcore shrine create        - (op) build a shrine where you stand
 * /hardcore shrine remove        - (op) unregister the nearest shrine within 8 blocks
 * /visit &lt;player&gt;                - dead spectators only: teleport to a player
 */
public final class HardcoreCommands {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // Only usable while dead and spectating. The check runs every time, so being bought back
        // (or an op taking you out of spectator) removes access immediately - nothing to revoke.
        d.register(Commands.literal("visit")
                .requires(HardcoreCommands::isDeadSpectator)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(HardcoreCommands::spectate)));

        d.register(Commands.literal("hardcore")
                .then(Commands.literal("dead").executes(HardcoreCommands::listDead))
                .then(Commands.literal("shrines").executes(HardcoreCommands::listShrines))
                .then(Commands.literal("price").executes(HardcoreCommands::showPrice)
                        .then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(HardcoreCommands::resetPrice)))
                .then(Commands.literal("revive").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        HardcoreData.get(ctx.getSource().getServer()).dead().values(), b))
                                .executes(HardcoreCommands::revive)))
                .then(Commands.literal("shrine").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("create").executes(HardcoreCommands::createShrine))
                        .then(Commands.literal("remove").executes(HardcoreCommands::removeShrine))));
    }

    private static boolean isDeadSpectator(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer p && isDeadSpectator(p);
    }

    public static boolean isDeadSpectator(ServerPlayer p) {
        return p.isSpectator() && HardcoreData.get(p.level().getServer()).isDead(p.getUUID());
    }

    private static int spectate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer self = src.getPlayerOrException();
        if (!isDeadSpectator(self)) {
            src.sendFailure(Component.literal("Only dead players in spectator mode can use /visit."));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (target == self) {
            src.sendFailure(Component.literal("You can't visit yourself."));
            return 0;
        }
        self.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), target.getYRot(), target.getXRot(), true);
        src.sendSuccess(() -> Component.literal("Teleported to " + target.getGameProfile().name() + ".").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** Chat line explaining /visit, with a clickable command that pre-fills "/visit " in the chat box. */
    public static Component visitHint(String prefix) {
        return visitHint(prefix, "/visit ");
    }

    public static Component visitHint(String prefix, String suggestion) {
        return Component.literal(prefix).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("/visit <player>").withStyle(st -> st.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent.SuggestCommand(suggestion))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to type /visit")))))
                .append(Component.literal(" to teleport to any online player (e.g. /visit Steve).").withStyle(ChatFormatting.YELLOW));
    }

    /** True if this dead spectator typed /tp or /teleport but isn't allowed to use it. */
    public static boolean shouldRedirectTp(ServerPlayer p, CommandSourceStack src, String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        String first = cmd.split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (!first.equals("tp") && !first.equals("teleport")) return false;
        if (!isDeadSpectator(p)) return false;
        var node = p.level().getServer().getCommands().getDispatcher().getRoot().getChild(first);
        return node == null || !node.canUse(src);
    }

    /** Re-sends the command list so /visit appears or disappears for this player right away. */
    public static void refresh(ServerPlayer p) {
        p.level().getServer().getCommands().sendCommands(p);
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

    private static int showPrice(CommandContext<CommandSourceStack> ctx) {
        HardcoreData data = HardcoreData.get(ctx.getSource().getServer());
        int n = data.revivesPurchased();
        ctx.getSource().sendSuccess(() -> Component.literal("Next buy-back costs " + data.currentReviveCost() + " diamonds ("
                + n + " bought back so far; the price doubles after each one, then "
                + HardcoreData.costForPurchase(n + 1) + ").").withStyle(ChatFormatting.AQUA), false);
        return data.currentReviveCost();
    }

    private static int resetPrice(CommandContext<CommandSourceStack> ctx) {
        HardcoreData data = HardcoreData.get(ctx.getSource().getServer());
        data.setRevivesPurchased(0);
        ctx.getSource().sendSuccess(() -> Component.literal("Buy-back price reset to " + data.currentReviveCost() + " diamonds.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
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
        src.sendSuccess(() -> Component.literal("Respawn Shrines (next buy-back costs " + data.currentReviveCost() + " diamonds):")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (GlobalPos g : list.subList(0, Math.min(5, list.size()))) {
            BlockPos p = g.pos();
            boolean sameDim = g.dimension().equals(src.getLevel().dimension());
            String dist = sameDim ? " - " + (int) Math.sqrt(p.distToCenterSqr(here)) + " blocks away" : "";
            src.sendSuccess(() -> Component.literal("  " + p.getX() + ", " + p.getY() + ", " + p.getZ()
                    + " (" + g.dimension().identifier().getPath() + ")" + dist).withStyle(ChatFormatting.WHITE), false);
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
