package com.santi.cs2bhop.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One player's bhop career. Immutable; every mutation returns a new record and the caller stores it
 * back into {@link BhopSaveData}.
 */
public record PlayerProgress(
        long points, int totalHops, double bestSpeed, int bestStreak, boolean phoonUnlocked) {

    public static final PlayerProgress EMPTY = new PlayerProgress(0L, 0, 0.0, 0, false);

    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.optionalFieldOf("points", 0L).forGetter(PlayerProgress::points),
                    Codec.INT.optionalFieldOf("total_hops", 0).forGetter(PlayerProgress::totalHops),
                    Codec.DOUBLE.optionalFieldOf("best_speed", 0.0).forGetter(PlayerProgress::bestSpeed),
                    Codec.INT.optionalFieldOf("best_streak", 0).forGetter(PlayerProgress::bestStreak),
                    Codec.BOOL.optionalFieldOf("phoon_unlocked", false).forGetter(PlayerProgress::phoonUnlocked))
            .apply(instance, PlayerProgress::new));

    public int level() {
        return BhopLevels.levelFor(points);
    }

    /** Records one counted hop. */
    public PlayerProgress withHop(long earned, double speed, int streak) {
        return new PlayerProgress(
                points + earned,
                totalHops + 1,
                Math.max(bestSpeed, speed),
                Math.max(bestStreak, streak),
                phoonUnlocked);
    }

    public PlayerProgress withPhoonUnlocked() {
        return phoonUnlocked ? this : new PlayerProgress(points, totalHops, bestSpeed, bestStreak, true);
    }
}
