package com.santi.cs2bhop.client.mixin;

import com.santi.cs2bhop.client.Cs2Movement;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla movement with CS2 movement for the local player.
 *
 * <p>{@code Player#travel} is the last override before {@code LivingEntity#travel} splits into
 * {@code travelInAir} / {@code travelInFluid}, both of which are private, so this is the lowest
 * point where the whole thing can be swapped out cleanly.
 */
@Mixin(Player.class)
public abstract class PlayerTravelMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void cs2bhop$travel(Vec3 input, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer local && Cs2Movement.apply(local, input)) {
            ci.cancel();
        }
    }
}
