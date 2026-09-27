package xyz.hardcoreserver;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A server-side-only menu shown to the client as a vanilla chest GUI. Each slot holds the head of a
 * dead player; clicking a head spends diamonds to bring that player back at this shrine.
 */
public class ReviveMenu extends ChestMenu {
    private static final MenuType<?>[] TYPES = {
            MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6
    };

    private final ServerLevel level;
    private final BlockPos anchor;
    private final List<UUID> targets;

    private ReviveMenu(int id, Inventory inv, SimpleContainer container, int rows, ServerLevel level, BlockPos anchor, List<UUID> targets) {
        super(TYPES[rows - 1], id, inv, container, rows);
        this.level = level;
        this.anchor = anchor;
        this.targets = targets;
    }

    /** Opens the revive GUI for {@code player} at the shrine anchored at {@code anchor}. */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos anchor) {
        HardcoreData data = HardcoreData.get(level.getServer());
        List<UUID> targets = new ArrayList<>();
        for (UUID id : data.dead().keySet()) {
            if (!data.isRevivePending(id) && !id.equals(player.getUUID())) targets.add(id);
            if (targets.size() == 54) break;
        }
        if (targets.isEmpty()) {
            player.sendSystemMessage(Component.literal("Nobody needs reviving right now. Everyone is alive!").withStyle(ChatFormatting.GREEN));
            return;
        }

        int cost = Config.REVIVE_COST.get();
        int rows = Math.min(6, (targets.size() + 8) / 9);
        SimpleContainer container = new SimpleContainer(rows * 9);
        for (int i = 0; i < targets.size(); i++) {
            UUID id = targets.get(i);
            container.setItem(i, headFor(level, id, data.dead().get(id), cost));
        }

        Component title = Component.literal("Respawn Shrine - " + cost + " diamonds each");
        player.openMenu(new SimpleMenuProvider((cid, inv, p) -> new ReviveMenu(cid, inv, container, rows, level, anchor, targets), title));
    }

    private static ItemStack headFor(ServerLevel level, UUID id, String name, int cost) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(id);
        GameProfile profile = online != null ? online.getGameProfile() : new GameProfile(id, name);
        head.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        head.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)));
        head.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(online != null ? "Online (spectating)" : "Offline").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                Component.literal("Cost: " + cost + " diamonds").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)),
                Component.literal("Click to buy back").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GREEN)))));
        return head;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Never let items move; just resync the client afterwards.
        if (player instanceof ServerPlayer sp && slotId >= 0 && slotId < targets.size()
                && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
            purchase(sp, targets.get(slotId));
        }
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level() == level
                && level.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR)
                && player.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5) <= 64.0;
    }

    private void purchase(ServerPlayer buyer, UUID target) {
        HardcoreData data = HardcoreData.get(buyer.server);
        String name = data.dead().get(target);
        if (name == null || data.isRevivePending(target)) {
            buyer.sendSystemMessage(Component.literal("That player has already been revived.").withStyle(ChatFormatting.YELLOW));
            buyer.closeContainer();
            return;
        }

        int cost = Config.REVIVE_COST.get();
        if (!buyer.isCreative()) {
            int have = buyer.getInventory().countItem(Items.DIAMOND);
            if (have < cost) {
                buyer.sendSystemMessage(Component.literal("You need " + cost + " diamonds to revive " + name + " (you have " + have + ").")
                        .withStyle(ChatFormatting.RED));
                level.playSound(null, anchor, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0F, 1.0F);
                return;
            }
            buyer.getInventory().clearOrCountMatchingItems(s -> s.is(Items.DIAMOND), cost, buyer.inventoryMenu.getCraftSlots());
            buyer.inventoryMenu.broadcastChanges();
        }

        data.queueRevive(target, Optional.of(GlobalPos.of(level.dimension(), ShrineBuilder.standPos(anchor))));
        buyer.closeContainer();
        level.playSound(null, anchor, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
        buyer.server.getPlayerList().broadcastSystemMessage(
                Component.literal(buyer.getGameProfile().getName() + " spent " + cost + " diamonds to buy back " + name + "!")
                        .withStyle(ChatFormatting.AQUA), false);
        if (buyer.server.getPlayerList().getPlayer(target) == null) {
            buyer.sendSystemMessage(Component.literal(name + " is offline and will be revived at this shrine when they next join.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
