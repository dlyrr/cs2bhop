package com.santi.cs2bhop.client;

import com.santi.cs2bhop.net.BhopPayloads;

/** Last boss-fight scoreboard the server sent. */
public final class ClientBossState {

    private static boolean active;
    private static long bossPoints;
    private static long playerPoints;
    private static int ticksLeft;
    private static boolean tired;

    private ClientBossState() {}

    public static void accept(BhopPayloads.BossSync sync) {
        active = sync.active();
        bossPoints = sync.bossPoints();
        playerPoints = sync.playerPoints();
        ticksLeft = sync.ticksLeft();
        tired = sync.tired();
    }

    public static void reset() {
        active = false;
        bossPoints = 0;
        playerPoints = 0;
        ticksLeft = 0;
        tired = false;
    }

    public static boolean active() {
        return active;
    }

    public static long bossPoints() {
        return bossPoints;
    }

    public static long playerPoints() {
        return playerPoints;
    }

    public static boolean tired() {
        return tired;
    }

    public static int ticksLeft() {
        return ticksLeft;
    }

    /** 0..1, how tense things are: rises as the scores converge and as time runs out. */
    public static double tension() {
        long gap = Math.abs(bossPoints - playerPoints);
        double closeness = 1.0 - Math.min(1.0, gap / 150.0);
        double urgency = 1.0 - Math.min(1.0, ticksLeft / 1200.0);
        return Math.max(0.35, Math.max(closeness, urgency));
    }
}

