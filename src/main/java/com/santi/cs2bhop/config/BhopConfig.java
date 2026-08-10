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
     * How soon after landing you must leave the ground again for the jump to count as a hop.
     * 2 ticks is tight enough that a normal jump never qualifies.
     */
    public int hopChainWindow = 2;

    /** Fraction of your level's run speed you must be moving at for a jump to count. */
    public double hopSpeedFraction = 0.9;

    /** Base points for one counted hop, before speed, boot and biome multipliers. */
    public double pointsPerHop = 1.0;

    /** Point multiplier while inside the bhop biome. */
    public double bhopBiomePointMultiplier = 1.5;

    /** Chain length that unlocks the Phoon Boots. */
    public int phoonUnlockStreak = 50;

    /** Cooldown on the boot shockwave, in ticks. */
    public int shockwaveCooldownTicks = 40;

    // ------------------------------------------------------------- mobs

    /** Whether some mobs spawn knowing how to bhop. */
    public boolean mobBhop = true;

    /** Fraction of mobs that are bhoppers. */
    public double mobBhopChance = 0.02;

    /** Speed in u/s at which a bhopping mob stops gaining. */
    public double mobBhopMaxSpeed = 500.0;

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

