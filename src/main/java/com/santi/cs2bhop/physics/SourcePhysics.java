package com.santi.cs2bhop.physics;

/**
 * A port of Source engine / CS2 player movement (CGameMovement).
 *
 * <p>Everything in this class works in <b>Source units per second</b> and is deliberately free of
 * any Minecraft imports, so the model can be reasoned about (and unit tested) on its own. The
 * mixin layer is responsible for converting to and from Minecraft's blocks-per-tick.
 *
 * <p>The three functions that actually matter for bunny hopping:
 *
 * <ul>
 *   <li>{@link #friction} — the ground drag that eats your speed. Bhopping is the art of never
 *       being on the ground long enough for this to run.
 *   <li>{@link #accelerate} — ground acceleration, capped at {@code maxSpeed}.
 *   <li>{@link #airAccelerate} — the one with the bug-turned-feature. {@code addspeed} is computed
 *       against a wish speed clamped to {@code airMaxWishSpeed} (30 u/s), but {@code accelspeed}
 *       uses the <i>unclamped</i> wish speed (250 u/s). That asymmetry is why steering into your
 *       strafe adds velocity perpendicular to your motion without ever being limited by how fast
 *       you are already going. Remove the clamp and airstrafing dies.
 * </ul>
 */
public final class SourcePhysics {

    /** Anything slower than this is snapped to a dead stop, as Source does. */
    private static final double STOP_EPSILON = 0.1;

    public double x;
    public double y;
    public double z;

    public SourcePhysics set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public double horizontalSpeed() {
        return Math.sqrt(x * x + z * z);
    }

    /**
     * Source's {@code CGameMovement::Friction}. Note the {@code stopSpeed} floor: below it, drag is
     * applied as if you were moving at {@code stopSpeed}, which is what makes low speeds bleed off
     * sharply instead of asymptotically.
     */
    public void friction(double friction, double stopSpeed, double dt) {
        double speed = horizontalSpeed();
        if (speed < STOP_EPSILON) {
            x = 0.0;
            z = 0.0;
            return;
        }

        double control = Math.max(speed, stopSpeed);
        double newSpeed = Math.max(0.0, speed - control * friction * dt);
        if (newSpeed != speed) {
            double scale = newSpeed / speed;
            x *= scale;
            z *= scale;
        }
    }

    /**
     * Source's {@code CGameMovement::Accelerate}. {@code wishX}/{@code wishZ} must be a unit vector
     * (or zero).
     */
    public void accelerate(double wishX, double wishZ, double wishSpeed, double accel, double dt) {
        double currentSpeed = x * wishX + z * wishZ;
        double addSpeed = wishSpeed - currentSpeed;
        if (addSpeed <= 0.0) {
            return;
        }

        double accelSpeed = Math.min(accel * wishSpeed * dt, addSpeed);
        x += accelSpeed * wishX;
        z += accelSpeed * wishZ;
    }

    /**
     * Source's {@code CGameMovement::AirAccelerate} — the heart of bunny hopping.
     *
     * <p>The clamped/unclamped wish speed split is not a mistake in this port; it is the original
     * behaviour. See the class javadoc.
     */
    public void airAccelerate(
            double wishX, double wishZ, double wishSpeed, double airAccel, double airMaxWishSpeed, double dt) {
        double clampedWishSpeed = Math.min(wishSpeed, airMaxWishSpeed);

        double currentSpeed = x * wishX + z * wishZ;
        double addSpeed = clampedWishSpeed - currentSpeed;
        if (addSpeed <= 0.0) {
            return;
        }

        // Deliberately the *unclamped* wishSpeed here.
        double accelSpeed = Math.min(airAccel * wishSpeed * dt, addSpeed);
        x += accelSpeed * wishX;
        z += accelSpeed * wishZ;
    }

    /** Hard clamp on horizontal speed, used for CS2's {@code sv_enablebunnyhopping 0} jump cap. */
    public void clampHorizontal(double maxHorizontal) {
        double speed = horizontalSpeed();
        if (speed > maxHorizontal && speed > 0.0) {
            double scale = maxHorizontal / speed;
            x *= scale;
            z *= scale;
        }
    }

    /**
     * Builds a normalised wish direction in world space from Minecraft-style movement input.
     *
     * @param forward forward input, +1 forward / -1 back
     * @param strafe strafe input, +1 <b>left</b> / -1 right (Minecraft's convention)
     * @param yawDegrees the player's yaw
     * @param out a length-2 array that receives {x, z}
     * @return the length of the raw input vector before normalising, 0 if there was no input
     */
    public static double wishDirection(double forward, double strafe, float yawDegrees, double[] out) {
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length < 1.0e-6) {
            out[0] = 0.0;
            out[1] = 0.0;
            return 0.0;
        }

        double f = forward / length;
        double s = strafe / length;

        double sin = Math.sin(Math.toRadians(yawDegrees));
        double cos = Math.cos(Math.toRadians(yawDegrees));

        // Matches Entity#getInputVector: x = strafe*cos - forward*sin, z = forward*cos + strafe*sin
        out[0] = s * cos - f * sin;
        out[1] = f * cos + s * sin;
        return length;
    }
}
