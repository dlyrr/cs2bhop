package com.santi.cs2bhop.client.mixin;

import com.santi.cs2bhop.client.MotionBlur;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens the field of view as you build speed.
 *
 * <p>This is half of the motion-blur effect and the half that does most of the work — the same
 * trick CS2 and every racing game uses. The vignette in {@link MotionBlur} handles the rest.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class FieldOfViewMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void cs2bhop$speedFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        if (!firstPerson || !((Object) this instanceof LocalPlayer)) {
            return;
        }

        float bonus = MotionBlur.fovBonus();
        if (bonus > 0.0F) {
            cir.setReturnValue(cir.getReturnValue() * (1.0F + bonus));
        }
    }
}
