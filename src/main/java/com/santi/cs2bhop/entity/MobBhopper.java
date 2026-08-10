package com.santi.cs2bhop.entity;

import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.physics.SourcePhysics;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Roughly 2% of mobs know how to bhop.
 *
 * <p>Membership is derived from the entity's UUID rather than stored, which keeps it stable across
 * saves and reloads with no attachment or persistence: a mob that bhops will always bhop, and one
 * that does not never will. {@code mobBhopChance} of 0.02 becomes a 1-in-50 bucket test.
 *
 * <p>They airstrafe optimally — wish direction exactly perpendicular to velocity, the same trick a
 * player is trying to hit by hand — so a bhopping zombie closes distance alarmingly well. Their
 * speed is capped well below what a levelled player can reach.
 */
public final class MobBhopper {

    private static final double TICKS_PER_SECOND = 20.0;

    private MobBhopper() {}

    public static boolean isBhopper(Mob mob) {
        BhopConfig config = BhopConfig.get();
        if (!config.mobBhop || config.mobBhopChance <= 0.0) {
            return false;
        }

        int buckets = (int) Math.round(1.0 / config.mobBhopChance);
        if (buckets <= 1) {
            return true;
        }

        UUID id = mob.getUUID();
        return Math.floorMod(Long.hashCode(id.getLeastSignificantBits() ^ id.getMostSignificantBits()), buckets) == 0;
    }

    public static void tick(Mob mob) {
        BhopConfig config = BhopConfig.get();
        if (!config.enabled || !isBhopper(mob)) {
            return;
        }
        if (mob.isPassenger() || mob.isInWater() || mob.isInLava() || mob.onClimbable() || mob.isNoGravity()) {
            return;
        }

        double unitsToBlocks = config.unitsToBlocks;
        double toUnits = TICKS_PER_SECOND / unitsToBlocks;
        double toBlocks = unitsToBlocks / TICKS_PER_SECOND;

        Vec3 delta = mob.getDeltaMovement();
        SourcePhysics velocity = new SourcePhysics();
        velocity.set(delta.x * toUnits, delta.y * toUnits, delta.z * toUnits);

        double horizontal = velocity.horizontalSpeed();

        // Only bhop when actually going somewhere; otherwise they twitch in place.
        if (horizontal < 40.0) {
            return;
        }

        if (mob.onGround()) {
            velocity.y = config.sv_jump_impulse;
        } else if (horizontal < config.mobBhopMaxSpeed) {
            // Perfect strafe: perpendicular to current motion.
            double wishX = -velocity.z / horizontal;
            double wishZ = velocity.x / horizontal;

            int substeps = config.effectiveSubticks();
            double dt = config.secondsPerSubtick();
            for (int i = 0; i < substeps; i++) {
                velocity.airAccelerate(
                        wishX, wishZ, config.sv_maxspeed, config.sv_airaccelerate, config.sv_air_max_wishspeed, dt);
            }
            velocity.clampHorizontal(config.mobBhopMaxSpeed);
        }

        mob.setDeltaMovement(velocity.x * toBlocks, velocity.y * toBlocks, velocity.z * toBlocks);

        if (mob.level() instanceof ServerLevel level && mob.onGround() && mob.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 0.1, mob.getZ(), 3, 0.2, 0.0, 0.2, 0.01);
        }
    }
}
