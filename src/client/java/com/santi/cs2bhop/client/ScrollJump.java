package com.santi.cs2bhop.client;

import com.santi.cs2bhop.config.BhopConfig;

/**
 * Binds the scroll wheel to jump, the way CS players do it (<code>bind mwheeldown +jump</code>).
 *
 * <p>Minecraft cannot express this as a {@code KeyMapping} — those only accept keyboard keys and
 * mouse buttons, and the wheel is neither — so scroll is intercepted in {@code MouseHandlerMixin}
 * and turned into a short <b>pulse</b> here.
 *
 * <p>The pulse is the whole point. A wheel notch is an instant event with no "held" state, so one
 * notch instead buys a few ticks during which touching the ground fires a jump immediately. That is
 * exactly why the bind works in CS: flicking the wheel sprays jump inputs across several ticks and
 * one of them lands on the tick you touch down, which is the frame-perfect timing you would
 * otherwise have to hit by hand.
 *
 * <p>Because of that, scroll jumping deliberately ignores the press/release rule that manual jumping
 * obeys — during a pulse, landing means jumping, even though the "key" never went up.
 */
public final class ScrollJump {

    private static int pulseTicks;

    private ScrollJump() {}

    /**
     * Called from the scroll hook.
     *
     * @param yOffset raw GLFW scroll offset; negative is down
     * @return true if the scroll was consumed and should not also move the hotbar
     */
    public static boolean handleScroll(double yOffset) {
        BhopConfig config = BhopConfig.get();
        if (!config.enabled || !config.scrollJump || yOffset == 0.0) {
            return false;
        }

        boolean matches =
                switch (config.scrollJumpDirection.toLowerCase()) {
                    case "up" -> yOffset > 0.0;
                    case "both" -> true;
                    default -> yOffset < 0.0;
                };

        if (!matches) {
            return false;
        }

        pulseTicks = Math.max(pulseTicks, Math.max(1, config.scrollJumpPulseTicks));
        return config.scrollJumpBlocksHotbar;
    }

    /** Whether a scroll-driven jump is currently wanted. */
    public static boolean jumping() {
        return pulseTicks > 0;
    }

    /** One notch should buy one jump, so the pulse is spent as soon as it fires. */
    public static void consume() {
        pulseTicks = 0;
    }

    public static void tick() {
        if (pulseTicks > 0) {
            pulseTicks--;
        }
    }

    public static void reset() {
        pulseTicks = 0;
    }
}
