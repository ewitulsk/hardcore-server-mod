package xyz.hardcoreserver.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.hardcoreserver.HardcoreEvents;

/** Eating a slice of cake (2 hunger) heals too. */
@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {
    @Inject(method = "eat", at = @At("RETURN"))
    private static void hardcoreserver$healOnCake(LevelAccessor level, BlockPos pos, BlockState state, Player player,
                                                  CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()) {
            HardcoreEvents.healFromFood(player, 2);
        }
    }
}
