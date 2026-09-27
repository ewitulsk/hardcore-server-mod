package xyz.hardcoreserver.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.hardcoreserver.ShrineEvents;

import java.util.ArrayList;
import java.util.List;

/** Explosions never destroy Respawn Shrine blocks. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void hardcoreserver$protectShrines(CallbackInfoReturnable<List<BlockPos>> cir) {
        cir.setReturnValue(ShrineEvents.filterExplosion(level, new ArrayList<>(cir.getReturnValue())));
    }
}
