package com.santi.cs2bhop.mixin;

import com.santi.cs2bhop.entity.MobBhopper;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobBhopMixin {

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void cs2bhop$bhop(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!self.level().isClientSide()) {
            MobBhopper.tick(self);
        }
    }
}
