package com.santi.cs2bhop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every CS2 movement cvar this mod reproduces, plus the handful of knobs that only exist because
 * Minecraft is not Counter-Strike.
 *
 * <p>Field names match the Source cvar they correspond to wherever one exists, so the config file
 * reads like a server config.
 */
public final class BhopConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("cs2bhop/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static BhopConfig instance;

    // ---------------------------------------------------------------- toggles

    /** Master switch. When false the mod does nothing and vanilla movement applies. */
    public boolean enabled = true;

    /**
     * {@code sv_autobunnyhopping}. When true, holding jump re-jumps automatically on the tick you
     * land, which is what every bhop/KZ server runs. When false you have to hit the timing by hand,
     * like matchmaking CS2.
     */
    public boolean autoBunnyHopping = true;

    /**
     * Inverse of {@code sv_enablebunnyhopping}. When true, horizontal speed is clamped to
     * {@code 1.104 * maxSpeed} on every jump — CS2's stock anti-bhop cap. Leave false to actually
     * gain speed.
     */
    public boolean bunnyHopSpeedCap = false;

    /**
     * CS2's stamina system, which makes each consecutive jump lower than the last. This is the real
     * reason bhopping is hard in matchmaking. Off by default; turn it on together with
     * {@code autoBunnyHopping = false} and {@code bunnyHopSpeedCap = true} for stock CS2 behaviour.
     */
    public boolean stamina = false;

    /**
     * Whether a hop still pays ground friction on the tick it takes off.
     *
     * <p>Source applies friction and then checks the jump button, so a textbook-perfect bhop eats
     * exactly one tick of drag — about 9% at 60 Hz. That is faithful, but it taxes every single hop
     * and makes speed climb far slower than a real {@code sv_autobunnyhopping} server feels, where
     * landing and jumping on the same tick keeps essentially all of it.
     *
     * <p>Left false, a clean hop keeps everything and only a botched one (staying on the ground)
     * costs you. Set true for the strictly faithful model.
     */
    public boolean frictionOnHopTick = false;

    /** Scroll wheel acts as jump, the CS {@code bind mwheeldown +jump} setup. */
    public boolean scrollJump = true;

    /** Which wheel direction jumps: {@code "down"}, {@code "up"} or {@code "both"}. */
    public String scrollJumpDirection = "down";

    /**
     * How many ticks one wheel notch keeps asking to jump. This is what makes the bind work — a
     * notch is instantaneous, so it has to cover a few ticks for one of them to land on the tick you
     * touch the ground.
     */
    public int scrollJumpPulseTicks = 3;

    /** Stop a jumping scroll from also cycling the hotbar. */
    public boolean scrollJumpBlocksHotbar = true;

    /** Speed-scaled FOV widening and edge vignette. */
    public boolean motionBlur = true;

    /** Draw the cl_showpos-style speedometer. */
    public boolean hud = true;

    /** Apply CS2 gravity and jump height. Turning this off keeps vanilla jump arcs. */
    public boolean sourceGravity = true;

    // ------------------------------------------------------------ Source cvars

    public double sv_maxspeed = 250.0;
    public double sv_accelerate = 5.5;
    public double sv_airaccelerate = 12.0;
    public double sv_friction = 5.2;
    public double sv_stopspeed = 80.0;

    /** The 30 u/s clamp that makes airstrafing work. Raising it makes gains absurd. */
    public double sv_air_max_wishspeed = 30.0;

    /** Units per second squared. Source default 800; vanilla Minecraft is equivalent to ~1280. */
    public double sv_gravity = 800.0;

    /** Units per second. 301.993 is CS2's, giving a 54.6 unit jump. */
    public double sv_jump_impulse = 301.993;

    /**
     * {@code +duck}. CS2 has no sprint, so Minecraft's sprint key is deliberately ignored — you
     * always move at {@code sv_maxspeed} unless you are crouching.
     */
    public double duckSpeedMultiplier = 0.34;

    // --------------------------------------------------- Minecraft-side tuning

    /**
     * Blocks per Source unit.
     *
     * <p>0.025 maps a 72-unit CS player onto Minecraft's 1.8-block player, which is the conversion
     * that keeps the game playable: it puts run speed at 6.25 b/s and jump height at ~1.4 blocks, so
     * you still clear a single block like you expect. The "physically correct" 0.01905 (1 unit =
     * 0.75 inch) gives a 1.04-block jump, and you can no longer step up a block.
     */
    public double unitsToBlocks = 0.025;

    /**
     * Velocity integration substeps per Minecraft tick.
     *
     * <p>Source's air acceleration is quantised per tick by the 30 u/s clamp, so running it at
     * Minecraft's 20 Hz would give roughly a third of CS2's strafe gain. 3 substeps puts the
     * integration at 60 Hz, close to a 64-tick server. Collision still runs once per tick.
     */
    public int subticks = 3;

    // ------------------------------------------------------- progression

    /** Ground run speed in u/s at level 50. Level 1 uses {@link #sv_maxspeed}. */
    public double maxLevelRunSpeed = 320.0;

    /** Hard ceiling on bhop speed in u/s at level 1. */
    public double baseSpeedCap = 700.0;

    /** Hard ceiling on bhop speed in u/s at level 50. */
    public double maxLevelSpeedCap = 2000.0;

    /**
     * How many ticks you may spend flat on the ground before a jump stops counting as a chained hop.
     *
     * <p>4 ticks is 0.2s. Standing or running along the ground racks up far more than that, so a
     * normal jump never qualifies — this is the condition that actually separates hops from jumps.
     */
    public int hopChainWindow = 4;

    /**
     * How many ticks flat on the ground end a chain outright.
     *
     * <p>Separate from {@link #hopChainWindow} on purpose. Using one number for both meant a single
     * scuffed landing — the kind flat ground produces constantly, since there is no slope to carry
     * you off — did not merely fail to score, it wiped a chain you had spent a minute building. Now
     * a missed hop costs you that hop, and only actually stopping costs you the chain.
     */
    public int chainGraceTicks = 12;

    /**
     * Fraction of your level's run speed you must be moving at for a jump to count.
     *
     * <p>Secondary to the chain window; it only exists to stop jumping on the spot from scoring.
     * Kept well below 1.0 because a threshold sitting right on top of run speed is fragile — server
     * speed is measured from movement packets and dips slightly whenever one arrives late.
     */
    public double hopSpeedFraction = 0.75;

    /** Base points for one counted hop, before speed, boot and biome multipliers. */
    public double pointsPerHop = 1.0;

    /** Point multiplier while inside the bhop biome. */
    public double bhopBiomePointMultiplier = 1.5;

    /** Cooldown on the boot shockwave, in ticks. */
    public int shockwaveCooldownTicks = 40;

    // ------------------------------------------------------------- mobs

    /** Whether some mobs spawn knowing how to bhop. */
    public boolean mobBhop = true;

    /** Fraction of mobs that are bhoppers. */
    public double mobBhopChance = 0.02;

    /** Speed in u/s at which a bhopping mob stops gaining. */
    public double mobBhopMaxSpeed = 500.0;

    // ------------------------------------------------------------- boss fight

    /** Length of the fight in seconds. */
    public int bossFightSeconds = 300;

    /** Points the boss accrues per second at level 1. Tuned so a clean bhop run is a dead heat. */
    public double bossPointsPerSecond = 3.5;

    /**
     * How much the boss's rate scales with your level.
     *
     * <p>Without this the fight inverts: a level 50 player carries a far higher speed ceiling, so
     * every hop is worth more and the boss gets lapped. Scaling its rate by your level keeps it a
     * race at both ends instead of a wall early and a formality later.
     */
    public double bossLevelScaling = 0.85;

    /** Points you lose when the boss lands a hit. */
    public double bossHitPlayerPenalty = 10.0;

    /** Points the boss loses when you land a hit. */
    public double playerHitBossPenalty = 30.0;

    /**
     * Points the boss loses per hit while it is tired.
     *
     * <p>Five times a normal hit, which is what makes the window worth breaking your chain for —
     * you give up hop points to close the distance, and it has to pay off.
     */
    public double bossTiredHitPenalty = 100.0;

    /** How long the boss stays tired, in ticks. Four seconds. */
    public int bossTiredDurationTicks = 80;

    /** Shortest and longest gap between tired windows, in seconds. */
    public int bossTiredMinIntervalSeconds = 22;

    public int bossTiredMaxIntervalSeconds = 40;

    /** Damage per boss hit, in health points. 5 is two and a half hearts. */
    public double bossAttackDamage = 5.0;

    public double bossHealth = 200.0;

    /**
     * Career points taken off you when PHOON wins.
     *
     * <p>The fight needs a downside or it is a free slot machine — you could summon it, ignore it
     * for five minutes and lose nothing. This is what makes the egg a wager.
     */
    public long bossLossCareerPenalty = 750;

    /** Give the six boots back when you win, so the egg is a wager rather than a sink. */
    public boolean bossReturnsBootsOnWin = true;

    /** PHOON blows you off your feet when it wins. Damage in health points. */
    public double bossVictoryDamage = 12.0;

    /** Radius the arena is flattened to. */
    public int bossArenaRadius = 40;

    /**
     * Radius the barriers close to by the end.
     *
     * <p>Kept generous on purpose: a bhopper at 600 u/s crosses 40 blocks in under three seconds,
     * so squeezing this much below 20 stops being a fight and starts being a corridor.
     */
    public int bossArenaMinRadius = 22;

    /** How far above the floor the arena is cleared. */
    public int bossArenaClearHeight = 8;

    /** Height of the barrier wall. */
    public int bossBarrierHeight = 10;

    /** Columns processed per tick while building or restoring, to avoid a freeze. */
    public int bossArenaBlocksPerTick = 400;

    /** Put the terrain back when the fight ends. Off means you keep the crater. */
    public boolean restoreArena = true;

    /** Ticks between boss dashes toward you. */
    public int bossDashInterval = 12;

    public double bossDashStrength = 0.85;

    /** Boss speed ceiling in u/s. */
    public double bossMaxSpeed = 700.0;

    public float bossSongVolume = 6.0F;

    /** Length of phoon.ogg in ticks, so it can be looped seamlessly (3:50). */
    public int phoonSongLengthTicks = 4600;

    // ------------------------------------------------------------------- stamina

    public double sv_staminamax = 80.0;
    public double sv_staminajumpcost = 0.080;
    public double sv_staminalandcost = 0.050;
    public double sv_staminarecoveryrate = 60.0;

    // --------------------------------------------------------------------- I/O

    public static BhopConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cs2bhop.json");
    }

    private static BhopConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                BhopConfig loaded = GSON.fromJson(reader, BhopConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (Exception e) {
                LOGGER.warn("Could not read {}, falling back to defaults", path, e);
            }
        }

        BhopConfig fresh = new BhopConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", path, e);
        }
    }

    // ------------------------------------------------------------- derived values

    /** Horizontal wish speed in u/s for the current stance. */
    public double wishSpeedFor(boolean ducking) {
        return ducking ? sv_maxspeed * duckSpeedMultiplier : sv_maxspeed;
    }

    public int effectiveSubticks() {
        return Math.max(1, subticks);
    }

    public double secondsPerSubtick() {
        return 0.05 / effectiveSubticks();
    }
}



