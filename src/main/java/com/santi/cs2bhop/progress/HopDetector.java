package com.santi.cs2bhop.progress;

/**
 * Decides whether a jump was a hop, from nothing but a stream of per-tick motion.
 *
 * <p>Deliberately free of Minecraft types so the decision rules can be simulated against synthetic
 * tick streams — a bhop chain, a standing jump, a running jump, jump spam — rather than only being
 * testable by playing the game.
 *
 * <p><b>Why vertical motion and not {@code onGround}.</b> The obvious implementation watches for the
 * tick where {@code onGround()} flips false. It looks right and fails: it needs the single grounded
 * tick to land in its own server tick, and client and server ticks are not locked together. A hop
 * whose landing and takeoff are processed in one server tick shows no grounded tick at all. Reading
 * speed from a single tick's position delta has the same flaw — a tick that receives no movement
 * packet reads as zero. So takeoff is a rise in vertical motion, and speed is a rolling peak.
 *
 * <p>{@code onGround} is still accepted as a hint for flatness, but nothing depends on it.
 */
public final class HopDetector {

    /** Vertical motion below this per tick counts as flat: standing, rather than rising or falling. */
    public static final double FLAT_EPSILON = 0.004;

    /** Ticks of horizontal speed kept, so one dropped movement packet cannot hide a hop. */
    public static final int SPEED_WINDOW = 4;

    public enum Verdict {
        /** Not a takeoff tick. */
        NONE,
        COUNTED,
        /** Took off, but had been sitting flat too long — an ordinary jump. */
        REJECTED_NOT_CHAINED,
        REJECTED_TOO_SLOW
    }

    private final double[] speedHistory = new double[SPEED_WINDOW];
    private int speedIndex;

    private int flatTicks;
    private boolean risingLastTick;
    private double peakSpeed;

    /**
     * Feeds one tick of motion.
     *
     * @param dy vertical blocks moved this tick
     * @param horizontalSpeed horizontal speed this tick, in Source units per second
     * @param onGround the server's view, used only as a flatness hint
     * @param jumpThreshold blocks of vertical motion that indicate a jump, typically half the impulse
     * @param requiredSpeed minimum speed in u/s for a hop to count
     * @param chainWindow how many flat ticks are still considered chained
     * @param eligible false in water, on ladders, while flying, and so on
     */
    public Verdict offer(
            double dy,
            double horizontalSpeed,
            boolean onGround,
            double jumpThreshold,
            double requiredSpeed,
            int chainWindow,
            boolean eligible) {

        speedHistory[speedIndex] = horizontalSpeed;
        speedIndex = (speedIndex + 1) % SPEED_WINDOW;

        peakSpeed = 0.0;
        for (double sample : speedHistory) {
            peakSpeed = Math.max(peakSpeed, sample);
        }

        boolean rising = dy > jumpThreshold;
        boolean flat = onGround || Math.abs(dy) < FLAT_EPSILON;

        Verdict verdict = Verdict.NONE;
        if (eligible && rising && !risingLastTick) {
            if (flatTicks > chainWindow) {
                verdict = Verdict.REJECTED_NOT_CHAINED;
            } else if (peakSpeed < requiredSpeed) {
                verdict = Verdict.REJECTED_TOO_SLOW;
            } else {
                verdict = Verdict.COUNTED;
            }
        }

        if (flat) {
            flatTicks++;
        } else {
            flatTicks = 0;
        }

        risingLastTick = rising;
        return verdict;
    }

    /** True once the player has been flat long enough that any chain is over. */
    public boolean chainBroken(int chainWindow) {
        return flatTicks > chainWindow;
    }

    public double peakSpeed() {
        return peakSpeed;
    }

    public int flatTicks() {
        return flatTicks;
    }

    public void reset() {
        java.util.Arrays.fill(speedHistory, 0.0);
        speedIndex = 0;
        flatTicks = 0;
        risingLastTick = false;
        peakSpeed = 0.0;
    }
}
