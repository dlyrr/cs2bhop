package com.santi.cs2bhop.client;

import com.santi.cs2bhop.config.BhopConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Speed-scaled motion blur.
 *
 * <p>Real post-process motion blur means running a shader chain over the framebuffer, which is both
 * heavy and the part of the renderer most likely to be rearranged between versions. This does the
 * two things that actually sell the effect and cost nothing: the field of view opens up (see
 * {@code FieldOfViewMixin}), and a soft vignette closes in from the screen edges with darker streaks
 * near the corners.
 *
 * <p>Intensity ramps from your level's run speed up to its ceiling and is capped well below opaque,
 * so at cruising speed it is barely there and at full tilt it frames the screen without hiding it.
 */
public final class MotionBlur implements HudElement {

    /** How dark the vignette gets at full speed. Deliberately modest. */
    private static final float MAX_ALPHA = 0.34F;

    /** Number of nested bands; more is smoother, 14 is already imperceptibly stepped. */
    private static final int BANDS = 14;

    /** How far in from the edge the vignette reaches, as a fraction of the screen. */
    private static final double MAX_REACH = 0.22;

    private static float intensity;

    /** 0..1, how fast you are relative to your level's envelope. */
    public static float intensity() {
        return intensity;
    }

    /** Extra FOV as a fraction, up to about 18% at the ceiling. */
    public static float fovBonus() {
        BhopConfig config = BhopConfig.get();
        return config.enabled && config.motionBlur ? intensity * 0.18F : 0.0F;
    }

    private static void updateIntensity(BhopConfig config) {
        double speed = Cs2BhopClient.state().previousSpeed;
        double floor = ClientProgress.runSpeed(config);
        double ceiling = Math.max(floor + 1.0, ClientProgress.speedCap(config));

        double raw = (speed - floor) / (ceiling - floor);
        double clamped = Math.max(0.0, Math.min(1.0, raw));

        // Ease in so normal running is completely clean and it only shows up once you are flying.
        double eased = clamped * clamped;

        // Smooth toward the target so landings do not make it flicker.
        intensity += (float) ((eased - intensity) * 0.25);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        BhopConfig config = BhopConfig.get();
        if (!config.enabled || !config.motionBlur) {
            intensity = 0.0F;
            return;
        }

        updateIntensity(config);
        if (intensity < 0.01F) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        int reachX = (int) (width * MAX_REACH * intensity);
        int reachY = (int) (height * MAX_REACH * intensity);
        if (reachX <= 0 || reachY <= 0) {
            return;
        }

        for (int band = 0; band < BANDS; band++) {
            // Outermost band is darkest.
            float t = 1.0F - (band / (float) BANDS);
            int alpha = (int) (MAX_ALPHA * intensity * t * t * 255.0F);
            if (alpha <= 0) {
                continue;
            }

            int colour = (alpha << 24);
            int insetX = reachX * band / BANDS;
            int insetY = reachY * band / BANDS;
            int stepX = Math.max(1, reachX / BANDS);
            int stepY = Math.max(1, reachY / BANDS);

            graphics.fill(0, insetY, width, insetY + stepY, colour);
            graphics.fill(0, height - insetY - stepY, width, height - insetY, colour);
            graphics.fill(insetX, insetY, insetX + stepX, height - insetY, colour);
            graphics.fill(width - insetX - stepX, insetY, width - insetX, height - insetY, colour);
        }
    }
}
