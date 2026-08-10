package com.santi.cs2bhop.client.mixin;

import com.santi.cs2bhop.client.Cs2Movement;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the vanilla jump impulse.
 *
 * <p>{@code aiStep} calls {@code jumpFromGround} just before {@code travel}, and also sets a
 * 10-tick {@code noJumpDelay} afterwards — which is exactly the thing that makes bunny hopping
 * impossible in vanilla. We issue our own impulse inside {@link Cs2Movement} with Source's timing,
 * so vanilla's is cancelled outright rather than fought with.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void cs2bhop$suppressVanillaJump(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer local && Cs2Movement.isActive(local)) {
            ci.cancel();
        }
    }
}
