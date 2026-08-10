package com.santi.cs2bhop.client;

import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.net.BhopPayloads;
import com.santi.cs2bhop.progress.BhopLevels;

/**
 * The last progression state the server sent us.
 *
 * <p>The client needs this because the physics run here: it cannot enforce a level-scaled speed
 * ceiling, or show you your level, without being told. Nothing here is authoritative — the server
 * counts the hops and owns the points.
 */
public final class ClientProgress {

    private static long points;
    private static int level = BhopLevels.MIN_LEVEL;
    private static int streak;
    private static double runSpeed = -1.0;
    private static double speedCap = -1.0;
    private static double pointMultiplier = 1.0;
    private static boolean inBhopBiome;

    private ClientProgress() {}

    public static void accept(BhopPayloads.ProgressSync sync) {
        points = sync.points();
        level = sync.level();
        streak = sync.streak();
        runSpeed = sync.runSpeed();
        speedCap = sync.speedCap();
        pointMultiplier = sync.pointMultiplier();
        inBhopBiome = sync.inBhopBiome();
    }

    /** Falls back to config defaults in singleplayer before the first sync arrives. */
    public static void reset() {
        points = 0;
        level = BhopLevels.MIN_LEVEL;
        streak = 0;
        runSpeed = -1.0;
        speedCap = -1.0;
        pointMultiplier = 1.0;
        inBhopBiome = false;
    }

    public static long points() {
        return points;
    }

    public static int level() {
        return level;
    }

    public static int streak() {
        return streak;
    }

    public static double pointMultiplier() {
        return pointMultiplier;
    }

    public static boolean inBhopBiome() {
        return inBhopBiome;
    }

    public static double runSpeed(BhopConfig config) {
        return runSpeed > 0.0 ? runSpeed : BhopLevels.runSpeed(level, config);
    }

    public static double speedCap(BhopConfig config) {
        return speedCap > 0.0 ? speedCap : BhopLevels.speedCap(level, config);
    }
}
