package xyz.hardcoreserver.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.hardcoreserver.HardcoreCommands;

/** Dead spectators who try /tp (without permission) are pointed to /visit instead of "Unknown command". */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void hardcoreserver$redirectTp(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        CommandSourceStack src = parse.getContext().getSource();
        if (src.getEntity() instanceof ServerPlayer player && HardcoreCommands.shouldRedirectTp(player, src, command)) {
            String[] parts = (command.startsWith("/") ? command.substring(1) : command).split(" ", 3);
            // "/tp Alice" -> clicking the hint fills in "/visit Alice"
            String suggestion = "/visit " + (parts.length > 1 && !parts[1].startsWith("@") ? parts[1] : "");
            player.sendSystemMessage(HardcoreCommands.visitHint("You can't use /" + parts[0] + ", but you can use ", suggestion));
            ci.cancel();
        }
    }
}
