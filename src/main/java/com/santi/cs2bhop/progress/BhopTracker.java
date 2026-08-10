package com.santi.cs2bhop.progress;

import com.santi.cs2bhop.Cs2Bhop;
import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.item.BhopBootsItem;
import com.santi.cs2bhop.item.BootTier;
import com.santi.cs2bhop.item.ModItems;
import com.santi.cs2bhop.net.BhopPayloads;
import com.santi.cs2bhop.sound.ModSounds;
import com.santi.cs2bhop.world.ModBiomes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Server-side hop detection and scoring.
 *
 * <p>Hops are detected here rather than reported by the client, so points cannot be spoofed by
 * sending fake packets. Everything needed is already server-authoritative: {@code onGround()} and
 * position are driven by movement packets that the server validates.
 *
 * <p><b>A jump only counts as a hop if both hold:</b>
 *
 * <ul>
 *   <li>it is <i>chained</i> — you left the ground within {@code hopChainWindow} ticks of landing,
 *       so the first jump out of a standstill never counts, and neither does jumping around while
 *       walking;
 *   <li>you were moving at least {@code hopSpeedFraction} of your level's run speed, so jumping on
 *       the spot is worth nothing no matter how fast you spam it.
 * </ul>
 */
public final class BhopTracker {

    private static final Map<UUID, State> STATES = new HashMap<>();

    private BhopTracker() {}

    private static final class State {
        int groundTicks;
        boolean onGroundLastTick = true;
        double lastX;
        double lastZ;
        boolean seeded;
        int streak;
        int shockwaveCooldown;
        long lastSyncedPoints = -1;
        int lastSyncedStreak = -1;
    }

    public static void clear(UUID id) {
        STATES.remove(id);
    }

    public static int streakOf(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? 0 : state.streak;
    }

    public static void tick(MinecraftServer server) {
        BhopConfig config = BhopConfig.get();
        BhopSaveData saveData = BhopSaveData.get(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
            tickPlayer(player, state, config, saveData);
        }
    }

    private static void tickPlayer(ServerPlayer player, State state, BhopConfig config, BhopSaveData saveData) {
        if (state.shockwaveCooldown > 0) {
            state.shockwaveCooldown--;
        }

        double x = player.getX();
        double z = player.getZ();
        if (!state.seeded) {
            state.lastX = x;
            state.lastZ = z;
            state.seeded = true;
        }

        double dx = x - state.lastX;
        double dz = z - state.lastZ;
        state.lastX = x;
        state.lastZ = z;

        // blocks/tick -> Source units/second
        double speed = Math.sqrt(dx * dx + dz * dz) * 20.0 / config.unitsToBlocks;

        boolean onGround = player.onGround();
        PlayerProgress progress = saveData.progress(player.getUUID());
        double runSpeed = BhopLevels.runSpeed(progress.level(), config);

        if (onGround) {
            state.groundTicks++;
        } else if (state.onGroundLastTick) {
            // Just left the ground: this is a takeoff, and the only place a hop can be scored.
            boolean chained = state.groundTicks <= config.hopChainWindow;
            boolean fastEnough = speed >= runSpeed * config.hopSpeedFraction;

            if (chained && fastEnough) {
                state.streak++;
                award(player, state, progress, speed, config, saveData);
            } else {
                state.streak = 0;
            }
            state.groundTicks = 0;
        }

        // Standing around ends the chain.
        if (onGround && state.groundTicks > config.hopChainWindow && state.streak > 0) {
            state.streak = 0;
        }

        state.onGroundLastTick = onGround;
        maybeSync(player, state, saveData, config);
    }

    private static void award(
            ServerPlayer player,
            State state,
            PlayerProgress progress,
            double speed,
            BhopConfig config,
            BhopSaveData saveData) {

        BootTier tier = BhopBootsItem.wornBy(player);
        double bootMultiplier = tier == null ? 1.0 : tier.pointMultiplier();
        boolean inBhopBiome = ModBiomes.isBhopBiome(player.level(), player.blockPosition());
        double biomeMultiplier = inBhopBiome ? config.bhopBiomePointMultiplier : 1.0;

        double runSpeed = Math.max(1.0, BhopLevels.runSpeed(progress.level(), config));
        double speedFactor = 1.0 + speed / runSpeed;

        long earned = Math.max(1L, Math.round(config.pointsPerHop * speedFactor * bootMultiplier * biomeMultiplier));

        int levelBefore = progress.level();
        PlayerProgress updated = progress.withHop(earned, speed, state.streak);

        if (!updated.phoonUnlocked() && state.streak >= config.phoonUnlockStreak) {
            updated = updated.withPhoonUnlocked();
            grantPhoonBoots(player);
        }

        saveData.put(player.getUUID(), player.getGameProfile().name(), updated);

        int levelAfter = updated.level();
        if (levelAfter > levelBefore) {
            announceLevelUp(player, levelAfter, config);
        }
    }

    private static void announceLevelUp(ServerPlayer player, int level, BhopConfig config) {
        player.sendSystemMessage(Component.literal("Bhop level " + level)
                .withStyle(level >= BhopLevels.MAX_LEVEL ? ChatFormatting.GOLD : ChatFormatting.AQUA));

        double run = BhopLevels.runSpeed(level, config);
        double cap = BhopLevels.speedCap(level, config);
        player.sendSystemMessage(Component.literal("  run %.0f u/s, ceiling %.0f u/s".formatted(run, cap))
                .withStyle(ChatFormatting.DARK_AQUA));

        player.level()
                .playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS,
                        0.6F,
                        1.4F);
    }

    private static void grantPhoonBoots(ServerPlayer player) {
        ItemStack stack = new ItemStack(ModItems.of(BootTier.PHOON));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        player.sendSystemMessage(Component.literal("The Phoon Boots have found you.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Handles the client pressing the boot ability key. */
    public static void releaseShockwave(ServerPlayer player) {
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());

        if (state.shockwaveCooldown > 0) {
            return;
        }

        BootTier tier = BhopBootsItem.wornBy(player);
        if (tier == null) {
            player.sendSystemMessage(
                    Component.literal("You are not wearing bhop boots.").withStyle(ChatFormatting.RED));
            return;
        }

        int banked = state.streak;
        if (banked <= 0) {
            player.sendSystemMessage(
                    Component.literal("No hops banked. Chain some jumps first.").withStyle(ChatFormatting.RED));
            return;
        }

        float damage = tier.shockwaveDamage(banked);
        double radius = tier.shockwaveRadius(banked);

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        AABB box = player.getBoundingBox().inflate(radius);
        int hit = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (target.distanceTo(player) > radius) {
                continue;
            }
            if (target.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
                hit++;
            }
        }

        level.sendParticles(
                ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 0.2, player.getZ(), (int) (radius * 8), radius / 2.0, 0.3, radius / 2.0, 0.0);
        level.sendParticles(
                ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), (int) (radius * 6), radius / 2.0, 0.1, radius / 2.0, 0.02);

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                tier == BootTier.PHOON ? ModSounds.PHOON : ModSounds.SHOCKWAVE,
                SoundSource.PLAYERS,
                tier == BootTier.PHOON ? 1.0F : 0.8F,
                1.0F);

        player.sendSystemMessage(Component.literal(
                        "%d hops released -> %.0f damage, %.1f blocks, %d hit".formatted(banked, damage, radius, hit))
                .withStyle(ChatFormatting.YELLOW));

        state.streak = 0;
        state.shockwaveCooldown = BhopConfig.get().shockwaveCooldownTicks;
    }

    private static void maybeSync(ServerPlayer player, State state, BhopSaveData saveData, BhopConfig config) {
        PlayerProgress progress = saveData.progress(player.getUUID());
        if (progress.points() == state.lastSyncedPoints && state.streak == state.lastSyncedStreak) {
            return;
        }
        state.lastSyncedPoints = progress.points();
        state.lastSyncedStreak = state.streak;

        BootTier tier = BhopBootsItem.wornBy(player);
        int level = progress.level();

        if (!ServerPlayNetworking.canSend(player, BhopPayloads.ProgressSync.TYPE)) {
            return;
        }

        ServerPlayNetworking.send(
                player,
                new BhopPayloads.ProgressSync(
                        progress.points(),
                        level,
                        state.streak,
                        BhopLevels.runSpeed(level, config),
                        BhopLevels.speedCap(level, config),
                        tier == null ? 1.0 : tier.pointMultiplier(),
                        ModBiomes.isBhopBiome(player.level(), player.blockPosition())));
    }

    /** Pushes a sync on join so the client is not stuck at level 1 defaults. */
    public static void syncNow(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        state.lastSyncedPoints = -1;
        state.lastSyncedStreak = -1;
        maybeSync(player, state, BhopSaveData.get(level.getServer()), BhopConfig.get());
        Cs2Bhop.LOGGER.debug("synced bhop progress to {}", player.getGameProfile().name());
    }
}
