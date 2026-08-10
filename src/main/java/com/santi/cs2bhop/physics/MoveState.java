package com.santi.cs2bhop.physics;

import com.santi.cs2bhop.config.BhopConfig;

/**
 * Per-player movement state that vanilla has nowhere to put: CS2 stamina, and the bookkeeping that
 * lets us tell a fresh jump from a held one.
 *
 * <p>Attached to every player through {@code Cs2Player}, implemented by a mixin on {@code Player}.
 */
public final class MoveState {

    /** CS2 stamina, 0 = fully rested. Higher means shorter jumps. */
    public double stamina;

    /** Whether the jump key was down last tick, so we can detect a fresh press. */
    public boolean jumpHeldLastTick;

    /** Whether we were on the ground last tick, used to detect landings. */
    public boolean onGroundLastTick;

    /** Set on the tick a jump is issued, so friction is skipped for that tick. */
    public boolean jumpedThisTick;

    /** Horizontal speed in u/s at the moment of the last takeoff, for the HUD's gain readout. */
    public double takeoffSpeed;

    /** Horizontal speed in u/s on the previous tick, for the HUD's delta readout. */
    public double previousSpeed;

    /** Consecutive successful hops, reset when you stay grounded. */
    public int hopStreak;

    /** Ticks spent on the ground since landing; a perfect bhop lands on 0. */
    public int groundTicks;

    /** Reusable velocity scratch so the hot path does not allocate. */
    public final SourcePhysics velocity = new SourcePhysics();

    /** Reusable wish-direction scratch, {x, z}. */
    public final double[] wishDir = new double[2];

    /** Clears everything, for when the local player is replaced (respawn, world change). */
    public void reset() {
        stamina = 0.0;
        jumpHeldLastTick = false;
        onGroundLastTick = false;
        jumpedThisTick = false;
        takeoffSpeed = 0.0;
        previousSpeed = 0.0;
        hopStreak = 0;
        groundTicks = 0;
        velocity.set(0.0, 0.0, 0.0);
    }

    /** Applied on landing. */
    public void onLand(BhopConfig config) {
        if (config.stamina) {
            stamina = Math.min(config.sv_staminamax, stamina + config.sv_staminamax * config.sv_staminalandcost);
        }
        groundTicks = 0;
    }

    /** Applied when a jump is issued. */
    public void onJump(BhopConfig config, double horizontalSpeed) {
        if (config.stamina) {
            stamina = Math.min(config.sv_staminamax, stamina + config.sv_staminamax * config.sv_staminajumpcost);
        }
        takeoffSpeed = horizontalSpeed;
        jumpedThisTick = true;
    }

    /** Recovers stamina, called once per tick. */
    public void tickStamina(BhopConfig config, double dt) {
        if (stamina > 0.0) {
            stamina = Math.max(0.0, stamina - config.sv_staminarecoveryrate * dt);
        }
    }

    /**
     * Multiplier applied to jump impulse. CS2 scales jump velocity down as stamina builds, which is
     * what makes the fourth hop in a row barely leave the ground.
     */
    public double jumpMultiplier(BhopConfig config) {
        if (!config.stamina || config.sv_staminamax <= 0.0) {
            return 1.0;
        }
        double ratio = (config.sv_staminamax - stamina) / config.sv_staminamax;
        return Math.max(0.2, Math.min(1.0, ratio));
    }
}
