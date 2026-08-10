package com.santi.cs2bhop.client.mixin;

import com.santi.cs2bhop.client.ScrollJump;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns wheel scrolls into jump input.
 *
 * <p>Injected at HEAD and cancelled only when the scroll is actually consumed as a jump, so
 * scrolling in menus, chat, the inventory and the spectator wheel all behave normally — those are
 * the cases the guards below rule out before we touch anything.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void cs2bhop$scrollJump(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (handle != minecraft.getWindow().handle()) {
            return;
        }

        // Only while actually playing: no screen, no overlay, and not the spectator fly-speed wheel.
        if (minecraft.screen != null
                || minecraft.getOverlay() != null
                || minecraft.player == null
                || minecraft.player.isSpectator()) {
            return;
        }

        if (ScrollJump.handleScroll(yoffset)) {
            ci.cancel();
        }
    }
}
