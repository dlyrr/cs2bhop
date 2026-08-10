package com.santi.cs2bhop.client;

import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.physics.MoveState;
import com.santi.cs2bhop.physics.SourcePhysics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Drives {@link SourcePhysics} from a {@link LocalPlayer}, replacing {@code Player#travel}.
 *
 * <p>Only the local player is simulated. Minecraft movement is client-authoritative — the server
 * takes whatever position the client reports as long as it is within ~100 m² per tick, and bhop
 * speeds are two orders of magnitude below that — so the client is the only place this needs to
 * run. Remote players are interpolated from packets and need nothing.
 *
 * <p>Order of operations inside a tick mirrors Source's {@code PlayerMove}: friction, then
 * {@code CheckJumpButton}, then {@code WalkMove} or {@code AirMove}, then gravity. Collision is
 * still Minecraft's, applied once at the end via {@link net.minecraft.world.entity.Entity#move}.
 */
public final class Cs2Movement {

    private static final double TICKS_PER_SECOND = 20.0;

    /** Vanilla gravity, in blocks per second squared, for when Source gravity is turned off. */
    private static final double VANILLA_GRAVITY_BPS2 = 0.08 * TICKS_PER_SECOND * TICKS_PER_SECOND;

    /** Vanilla jump impulse, in blocks per second. */
    private static final double VANILLA_JUMP_BPS = 0.42 * TICKS_PER_SECOND;

    private Cs2Movement() {}

    /**
     * Whether CS2 movement applies right now. Anything Minecraft handles in a way Source has no
     * concept of — swimming, ladders, elytra, creative flight, riding — falls back to vanilla.
     */
    public static boolean isActive(LocalPlayer player) {
        BhopConfig config = BhopConfig.get();
        return config.enabled
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isPassenger()
                && !player.isFallFlying()
                && !player.onClimbable()
                && !player.isSwimming()
                && !player.isInWater()
                && !player.isInLava();
    }

    /**
     * Runs one tick of CS2 movement.
     *
     * @param input Minecraft's raw movement input, {@code x} = strafe (left positive), {@code z} =
     *     forward. Only its direction is used; the magnitude is vanilla's speed scaling, which we
     *     replace with {@code sv_maxspeed}.
     * @return true if movement was handled and vanilla travel should be cancelled
     */
    public static boolean apply(LocalPlayer player, Vec3 input) {
        if (!isActive(player)) {
            return false;
        }

        BhopConfig config = BhopConfig.get();
        MoveState state = Cs2BhopClient.state();

        double unitsToBlocks = config.unitsToBlocks;
        int substeps = config.effectiveSubticks();
        double dt = config.secondsPerSubtick();

        // Minecraft stores blocks per tick; Source constants are units per second.
        double toUnits = TICKS_PER_SECOND / unitsToBlocks;
        double toBlocks = unitsToBlocks / TICKS_PER_SECOND;

        SourcePhysics velocity = state.velocity;
        Vec3 delta = player.getDeltaMovement();
        velocity.set(delta.x * toUnits, delta.y * toUnits, delta.z * toUnits);

        boolean onGround = player.onGround();
        boolean jumpHeld = player.input.keyPresses.jump();
        boolean ducking = player.isCrouching();

        if (onGround) {
            if (!state.onGroundLastTick) {
                state.onLand(config);
            }
            state.groundTicks++;
        } else {
            state.groundTicks = 0;
        }

        // Autobhop re-fires while held; otherwise you have to release and press again, and hitting
        // the landing tick by hand is the whole skill.
        boolean wantJump = jumpHeld && (config.autoBunnyHopping || !state.jumpHeldLastTick);

        SourcePhysics.wishDirection(input.z, input.x, player.getYRot(), state.wishDir);
        double wishX = state.wishDir[0];
        double wishZ = state.wishDir[1];

        // Both ends of the speed envelope scale with your bhop level.
        double runSpeed = ClientProgress.runSpeed(config);
        double wishSpeed = ducking ? runSpeed * config.duckSpeedMultiplier : runSpeed;
        double speedCap = ClientProgress.speedCap(config);

        double gravity = config.sourceGravity ? config.sv_gravity : VANILLA_GRAVITY_BPS2 / unitsToBlocks;
        double jumpImpulse = config.sourceGravity ? config.sv_jump_impulse : VANILLA_JUMP_BPS / unitsToBlocks;

        state.jumpedThisTick = false;
        boolean airborne = !onGround;

        for (int step = 0; step < substeps; step++) {
            if (!airborne) {
                boolean takingOff = step == 0 && wantJump;

                // A clean hop leaves before friction can bite. Staying grounded still costs you.
                if (!takingOff || config.frictionOnHopTick) {
                    velocity.friction(config.sv_friction, config.sv_stopspeed, dt);
                }

                if (takingOff) {
                    if (config.bunnyHopSpeedCap) {
                        // sv_enablebunnyhopping 0: CS2 shaves you back to 110.4% of max on takeoff.
                        velocity.clampHorizontal(runSpeed * 1.104);
                    }

                    velocity.y = jumpImpulse * state.jumpMultiplier(config);
                    state.hopStreak = state.groundTicks <= 1 ? state.hopStreak + 1 : 1;
                    state.onJump(config, velocity.horizontalSpeed());
                    airborne = true;
                } else {
                    velocity.accelerate(wishX, wishZ, wishSpeed, config.sv_accelerate, dt);
                }
            }

            if (airborne) {
                velocity.airAccelerate(
                        wishX, wishZ, wishSpeed, config.sv_airaccelerate, config.sv_air_max_wishspeed, dt);
            }

            velocity.y -= gravity * dt;
        }

        // The level ceiling. Without it, a long enough chain grows without bound.
        velocity.clampHorizontal(speedCap);

        state.tickStamina(config, 0.05);
        state.previousSpeed = velocity.horizontalSpeed();

        if (onGround && !state.jumpedThisTick && state.groundTicks > 3) {
            state.hopStreak = 0;
        }
        state.onGroundLastTick = onGround;
        state.jumpHeldLastTick = jumpHeld;

        player.setDeltaMovement(velocity.x * toBlocks, velocity.y * toBlocks, velocity.z * toBlocks);
        player.move(MoverType.SELF, player.getDeltaMovement());
        return true;
    }
}
