package xyz.hardcoreserver.mixin;

import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.hardcoreserver.Config;

/**
 * Forces every world to report itself as hardcore. This is what vanilla clients read from the
 * login packet (hardcore hearts, "Spectate World" death screen) and what locks difficulty to Hard.
 */
@Mixin(PrimaryLevelData.class)
public abstract class PrimaryLevelDataMixin {
    @Inject(method = "isHardcore", at = @At("HEAD"), cancellable = true)
    private void hardcoreserver$forceHardcore(CallbackInfoReturnable<Boolean> cir) {
        if (Config.forceHardcoreSafe()) {
            cir.setReturnValue(true);
        }
    }
}
