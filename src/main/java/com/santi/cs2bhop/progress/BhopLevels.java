package com.santi.cs2bhop.progress;

import com.santi.cs2bhop.config.BhopConfig;

/**
 * The level curve, and what levelling up actually buys you.
 *
 * <p>Levels run 1 to 50. Both ends of your speed envelope scale with level: the floor
 * ({@code sv_maxspeed}, how fast you run and therefore how fast you leave the ground) and the
 * ceiling (the hard cap on how much speed a chain can build). At level 1 you are a stock CS2 player
 * who tops out fairly quickly; at level 50 you leave the ground faster than you could previously
 * ever get going, and the cap is high enough that the square-root growth is the only thing slowing
 * you down.
 */
public final class BhopLevels {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 50;

    private BhopLevels() {}

    /**
     * Cumulative points needed to reach {@code level}. Level 1 is free.
     *
     * <p>The 1.6 exponent means early levels come quickly and the last few are a real grind:
     * reaching 50 is roughly 40k points, which at a couple of points per hop is a few hours of
     * clean bhopping.
     */
    public static long pointsForLevel(int level) {
        int clamped = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        return Math.round(80.0 * Math.pow(clamped - 1, 1.6));
    }

    public static int levelFor(long points) {
        for (int level = MAX_LEVEL; level > MIN_LEVEL; level--) {
            if (points >= pointsForLevel(level)) {
                return level;
            }
        }
        return MIN_LEVEL;
    }

    /** Points still needed for the next level, or 0 at max. */
    public static long pointsToNext(long points) {
        int level = levelFor(points);
        return level >= MAX_LEVEL ? 0L : pointsForLevel(level + 1) - points;
    }

    /** 0..1 through the current level, 1 at max level. */
    public static double levelProgress(long points) {
        int level = levelFor(points);
        if (level >= MAX_LEVEL) {
            return 1.0;
        }
        long floor = pointsForLevel(level);
        long ceiling = pointsForLevel(level + 1);
        return ceiling <= floor ? 1.0 : (double) (points - floor) / (ceiling - floor);
    }

    private static double lerpByLevel(int level, double atLevel1, double atLevel50) {
        int clamped = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        double t = (clamped - MIN_LEVEL) / (double) (MAX_LEVEL - MIN_LEVEL);
        return atLevel1 + (atLevel50 - atLevel1) * t;
    }

    /** Ground run speed in u/s — the speed you take off at, and the floor of your envelope. */
    public static double runSpeed(int level, BhopConfig config) {
        return lerpByLevel(level, config.sv_maxspeed, config.maxLevelRunSpeed);
    }

    /** Hard ceiling on horizontal bhop speed in u/s. */
    public static double speedCap(int level, BhopConfig config) {
        return lerpByLevel(level, config.baseSpeedCap, config.maxLevelSpeedCap);
    }
}
