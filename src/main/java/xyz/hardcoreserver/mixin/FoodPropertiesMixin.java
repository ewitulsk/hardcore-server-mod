package xyz.hardcoreserver.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.hardcoreserver.HardcoreEvents;

/** Eating any food item heals the player directly. */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
    @Inject(method = "onConsume", at = @At("TAIL"))
    private void hardcoreserver$healOnEat(Level level, LivingEntity user, ItemStack stack, Consumable consumable, CallbackInfo ci) {
        if (user instanceof Player player) {
            HardcoreEvents.healFromFood(player, ((FoodProperties) (Object) this).nutrition());
        }
    }
}
