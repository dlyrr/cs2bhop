package com.santi.cs2bhop.boss;

import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.net.BhopPayloads;
import com.santi.cs2bhop.sound.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Phoon boss fight: a five-minute points race on a flattened arena.
 *
 * <p>You do not kill the boss, you outscore it. It accrues points steadily and always comes
 * straight at you; every hit it lands costs you 10 points and interrupts your chain, and every hit
 * you land costs it 20. The arena is levelled so there is nothing to bhop off but flat ground, and
 * barrier walls close in over the fight so you cannot simply outrun it forever.
 *
 * <p>One fight at a time per server — it is an event, not an ambient mob.
 *
 * <p><b>The arena is restored when the fight ends.</b> Flattening a 40-block radius permanently
 * would quietly bulldoze whatever you summoned it on top of, so every changed block is snapshotted
 * first and put back afterwards. Set {@code restoreArena} false if you actually want the crater.
 */
public final class PhoonBossFight {

    private enum Phase {
        BUILDING,
        FIGHTING,
        RESTORING,
        DONE
    }

    private static PhoonBossFight active;

    private final ServerLevel level;
    private final UUID playerId;
    private final String playerName;
    private final BlockPos centre;
    private final int floorY;
    private final BhopConfig config;

    private final ServerBossEvent bar = new ServerBossEvent(
            UUID.randomUUID(),
            Component.literal("PHOON").withStyle(ChatFormatting.LIGHT_PURPLE),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);

    /** Original states of every block we touched, so the arena can be put back. */
    private final Map<BlockPos, BlockState> snapshot = new HashMap<>();

    private final List<BlockPos> columns = new ArrayList<>();
    private final Set<BlockPos> ring = new HashSet<>();
    private final List<BlockPos> restoreQueue = new ArrayList<>();

    private Phase phase = Phase.BUILDING;
    private int cursor;
    private int restoreCursor;

    private Mob boss;
    private int ticksLeft;
    private int songTimer;
    private int barrierRadius;

    private double bossPoints;
    private double playerPoints;

    /** Ticks of tired left; 0 means it is up and hunting. */
    private int tiredTicks;

    /** Ticks until the next tired window. */
    private int tiredCountdown;

    private PhoonBossFight(ServerLevel level, ServerPlayer player, BlockPos centre) {
        this.level = level;
        this.playerId = player.getUUID();
        this.playerName = player.getGameProfile().name();
        this.centre = centre;
        this.floorY = centre.getY() - 1;
        this.config = BhopConfig.get();
        this.ticksLeft = config.bossFightSeconds * 20;
        this.barrierRadius = config.bossArenaRadius;
        scheduleTired();

        int r = config.bossArenaRadius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r) {
                    columns.add(centre.offset(dx, 0, dz));
                }
            }
        }
    }

    public static boolean isActive() {
        return active != null && active.phase != Phase.DONE;
    }

    public static PhoonBossFight current() {
        return active;
    }

    /** @return null if a fight is already running */
    public static PhoonBossFight start(ServerLevel level, ServerPlayer player, BlockPos centre) {
        if (isActive()) {
            return null;
        }

        PhoonBossFight fight = new PhoonBossFight(level, player, centre);
        active = fight;
        fight.begin(player);
        return fight;
    }

    private void begin(ServerPlayer player) {
        bar.addPlayer(player);
        bar.setProgress(1.0F);

        player.sendSystemMessage(Component.literal("PHOON has entered the arena.")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Outscore it. Hits cost you points; hit back to cost it more.")
                .withStyle(ChatFormatting.GRAY));

        playSong();
    }

    // ------------------------------------------------------------------ ticking

    public static void tickActive() {
        if (active != null) {
            active.tick();
            if (active.phase == Phase.DONE) {
                active = null;
            }
        }
    }

    private void tick() {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);

        switch (phase) {
            case BUILDING -> tickBuild(player);
            case FIGHTING -> tickFight(player);
            case RESTORING -> tickRestore();
            case DONE -> {}
        }

        if (player != null) {
            sync(player);
        }
    }

    private void tickBuild(ServerPlayer player) {
        int budget = config.bossArenaBlocksPerTick;
        while (budget-- > 0 && cursor < columns.size()) {
            flattenColumn(columns.get(cursor++));
        }

        if (cursor >= columns.size()) {
            rebuildBarrier(barrierRadius);
            spawnBoss(player);
            phase = Phase.FIGHTING;
        }
    }

    private void tickFight(ServerPlayer player) {
        if (player == null) {
            // Logged out mid-fight: tear the arena down, nothing to penalise.
            finish(null, false, null);
            return;
        }

        if (!player.isAlive()) {
            // Dying is a loss, with the same stakes as being outscored.
            finish(player, false, "It put you down.");
            return;
        }

        ticksLeft--;
        bar.setProgress(Math.max(0.0F, ticksLeft / (float) (config.bossFightSeconds * 20)));

        // A tired boss stops scoring, which is half of why the window matters.
        if (tiredTicks <= 0) {
            bossPoints += bossRate() / 20.0;
        }

        tickTired(player);

        // Keep the player inside; the barrier is solid but a bhopper can clip corners at speed.
        double dist = Math.sqrt(player.distanceToSqr(centre.getX() + 0.5, player.getY(), centre.getZ() + 0.5));
        if (dist > barrierRadius) {
            Vec3 towards = new Vec3(centre.getX() + 0.5 - player.getX(), 0, centre.getZ() + 0.5 - player.getZ())
                    .normalize()
                    .scale(0.6);
            player.setDeltaMovement(player.getDeltaMovement().add(towards));
            player.hurtMarked = true;
        }

        tickBarrier();
        tickBoss(player);
        tickSong();

        if (ticksLeft <= 0) {
            boolean won = playerPoints > bossPoints;
            finish(player, won, null);
        }
    }

    private void tickBarrier() {
        int total = config.bossFightSeconds * 20;
        double progress = 1.0 - ticksLeft / (double) total;
        int target = (int) Math.round(
                config.bossArenaRadius - (config.bossArenaRadius - config.bossArenaMinRadius) * progress);

        if (target < barrierRadius) {
            barrierRadius = target;
            rebuildBarrier(barrierRadius);
        }
    }

    /**
     * The boss's scoring rate, scaled by the fighter's level.
     *
     * <p>A level 50 player carries a far higher speed ceiling, so every hop is worth more. Without
     * scaling, the fight is a wall at level 1 and a formality at 50.
     */
    private double bossRate() {
        int playerLevel = com.santi.cs2bhop.progress.BhopSaveData.get(level.getServer())
                .progress(playerId)
                .level();
        double fraction = (playerLevel - 1) / (double) (com.santi.cs2bhop.progress.BhopLevels.MAX_LEVEL - 1);
        return config.bossPointsPerSecond * (1.0 + config.bossLevelScaling * fraction);
    }

    private void tickTired(ServerPlayer player) {
        if (tiredTicks > 0) {
            tiredTicks--;

            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                    boss == null ? centre.getX() : boss.getX(),
                    (boss == null ? centre.getY() : boss.getY()) + 2.2,
                    boss == null ? centre.getZ() : boss.getZ(),
                    2,
                    0.3,
                    0.2,
                    0.3,
                    0.0);

            if (tiredTicks == 0) {
                scheduleTired();
                player.sendSystemMessage(
                        Component.literal("PHOON is up again.").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }

        if (--tiredCountdown <= 0) {
            tiredTicks = config.bossTiredDurationTicks;
            player.sendSystemMessage(Component.literal("PHOON IS TIRED — HIT IT")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            level.playSound(
                    null,
                    boss == null ? centre.getX() : boss.getX(),
                    boss == null ? centre.getY() : boss.getY(),
                    boss == null ? centre.getZ() : boss.getZ(),
                    net.minecraft.sounds.SoundEvents.ILLUSIONER_PREPARE_BLINDNESS,
                    SoundSource.HOSTILE,
                    1.2F,
                    0.6F);
        }
    }

    private void scheduleTired() {
        int min = config.bossTiredMinIntervalSeconds * 20;
        int max = Math.max(min + 1, config.bossTiredMaxIntervalSeconds * 20);
        tiredCountdown = min + level.getRandom().nextInt(max - min);
    }

    public boolean isTired() {
        return tiredTicks > 0;
    }

    private void tickBoss(ServerPlayer player) {
        if (boss == null || !boss.isAlive()) {
            spawnBoss(player);
            return;
        }

        // Tired: it stops hunting and stands there. That is the opening.
        if (tiredTicks > 0) {
            boss.setTarget(null);
            boss.setAggressive(false);
            boss.setDeltaMovement(boss.getDeltaMovement().multiply(0.5, 1.0, 0.5));
            return;
        }

        // Otherwise it always chooses violence.
        boss.setTarget(player);
        boss.setAggressive(true);

        // No vanilla mob can walk down a bhopper, so it bhops too and steers by the routine in
        // config/cs2bhop_boss_moves.json — see BossChoreography.
        int fightTick = config.bossFightSeconds * 20 - ticksLeft;
        Vec3 delta = boss.getDeltaMovement();

        if (boss.onGround()) {
            double lift = config.sv_jump_impulse * config.unitsToBlocks / 20.0;
            delta = new Vec3(delta.x, BossChoreography.isLeap(fightTick) ? lift * 1.5 : lift, delta.z);
        }

        Vec3 steer = BossChoreography.steer(boss, player, fightTick, config.bossDashStrength);
        if (boss.tickCount % Math.max(1, config.bossDashInterval) == 0) {
            delta = delta.add(steer);
        } else {
            delta = delta.add(steer.scale(0.25));
        }

        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double cap = config.bossMaxSpeed * config.unitsToBlocks / 20.0;
        if (horizontal > cap) {
            double scale = cap / horizontal;
            delta = new Vec3(delta.x * scale, delta.y, delta.z * scale);
        }

        boss.setDeltaMovement(delta);
        boss.hurtMarked = true;

        // Never let it wander off or drown in its own arena.
        if (boss.distanceToSqr(centre.getX() + 0.5, boss.getY(), centre.getZ() + 0.5)
                > (double) barrierRadius * barrierRadius) {
            boss.teleportTo(player.getX(), player.getY() + 1, player.getZ());
        }
    }

    private void tickSong() {
        if (--songTimer <= 0) {
            playSong();
        }
    }

    private void tickRestore() {
        int budget = config.bossArenaBlocksPerTick * 4;
        while (budget-- > 0 && restoreCursor < restoreQueue.size()) {
            BlockPos pos = restoreQueue.get(restoreCursor++);
            level.setBlock(pos, snapshot.get(pos), 2);
        }

        if (restoreCursor >= restoreQueue.size()) {
            phase = Phase.DONE;
        }
    }

    // ------------------------------------------------------------------ scoring

    public boolean isFighter(UUID id) {
        return playerId.equals(id) && phase == Phase.FIGHTING;
    }

    public void addPlayerPoints(double points) {
        if (phase == Phase.FIGHTING) {
            playerPoints += points;
        }
    }

    /** The boss landed a hit. */
    public void onPlayerHit(ServerPlayer player) {
        if (phase != Phase.FIGHTING) {
            return;
        }
        playerPoints = Math.max(0.0, playerPoints - config.bossHitPlayerPenalty);
        player.sendSystemMessage(
                Component.literal("-%.0f points".formatted(config.bossHitPlayerPenalty)).withStyle(ChatFormatting.RED),
                true);
    }

    /** You landed a hit. Worth five times as much during a tired window. */
    public void onBossHit(ServerPlayer player) {
        if (phase != Phase.FIGHTING) {
            return;
        }

        boolean tired = tiredTicks > 0;
        double penalty = tired ? config.bossTiredHitPenalty : config.playerHitBossPenalty;
        bossPoints = Math.max(0.0, bossPoints - penalty);

        player.sendSystemMessage(
                Component.literal("PHOON -%.0f points%s".formatted(penalty, tired ? "  (TIRED)" : ""))
                        .withStyle(tired ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                true);
    }

    public boolean isBoss(java.util.UUID id) {
        return boss != null && boss.getUUID().equals(id);
    }

    // ------------------------------------------------------------------ lifecycle

    private void finish(ServerPlayer player, boolean won, String reason) {
        if (boss != null) {
            boss.discard();
            boss = null;
        }

        bar.removeAllPlayers();
        stopSong(player);

        if (player != null) {
            if (won) {
                player.sendSystemMessage(Component.literal("You outran PHOON. %,.0f to %,.0f."
                                .formatted(playerPoints, bossPoints))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                awardVictory(player);
            } else {
                player.sendSystemMessage(Component.literal(reason != null
                                ? "PHOON wins. " + reason
                                : "PHOON wins. %,.0f to %,.0f.".formatted(bossPoints, playerPoints))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                applyDefeat(player);
            }

            sync(player);
        }

        if (config.restoreArena) {
            restoreQueue.addAll(snapshot.keySet());
            phase = Phase.RESTORING;
        } else {
            phase = Phase.DONE;
        }
    }

    /**
     * Win: the Phoon Boots, which exist nowhere else — no recipe, no creative tab, no drop. Plus the
     * five wagered boots back, so the egg is a wager rather than a sink.
     */
    private void awardVictory(ServerPlayer player) {
        give(player, new net.minecraft.world.item.ItemStack(
                com.santi.cs2bhop.item.ModItems.of(com.santi.cs2bhop.item.BootTier.PHOON)));

        player.sendSystemMessage(Component.literal("The Phoon Boots are yours.")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

        var saveData = com.santi.cs2bhop.progress.BhopSaveData.get(level.getServer());
        saveData.put(
                playerId,
                player.getGameProfile().name(),
                saveData.progress(playerId).withPhoonUnlocked());

        if (config.bossReturnsBootsOnWin) {
            for (com.santi.cs2bhop.item.BootTier tier : com.santi.cs2bhop.item.BhopBootsItem.obtainableTiers()) {
                give(player, new net.minecraft.world.item.ItemStack(com.santi.cs2bhop.item.ModItems.of(tier)));
            }
            player.sendSystemMessage(
                    Component.literal("Your wagered boots are returned.").withStyle(ChatFormatting.GREEN));
        }
    }

    /**
     * Loss: the boots stay gone, career points are docked, and PHOON puts you on the floor.
     *
     * <p>Without this the egg is a free slot machine — you could summon it, ignore it for five
     * minutes and be no worse off.
     */
    private void applyDefeat(ServerPlayer player) {
        var saveData = com.santi.cs2bhop.progress.BhopSaveData.get(level.getServer());
        var progress = saveData.progress(playerId);

        long penalty = Math.min(config.bossLossCareerPenalty, progress.points());
        if (penalty > 0) {
            saveData.put(
                    playerId,
                    player.getGameProfile().name(),
                    new com.santi.cs2bhop.progress.PlayerProgress(
                            progress.points() - penalty,
                            progress.totalHops(),
                            progress.bestSpeed(),
                            progress.bestStreak(),
                            progress.phoonUnlocked()));

            player.sendSystemMessage(
                    Component.literal("−%,d career points. Your boots are gone.".formatted(penalty))
                            .withStyle(ChatFormatting.RED));
        } else {
            player.sendSystemMessage(
                    Component.literal("Your boots are gone.").withStyle(ChatFormatting.RED));
        }

        if (config.bossVictoryDamage > 0 && player.isAlive()) {
            player.hurtServer(level, player.damageSources().magic(), (float) config.bossVictoryDamage);
            player.setDeltaMovement(player.getDeltaMovement().add(0.0, 1.1, 0.0));
            player.hurtMarked = true;
        }

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE,
                0.8F,
                0.7F);
    }

    private static void give(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Called on server shutdown so a half-built arena is not left behind. */
    public static void abortActive() {
        if (active != null) {
            active.finish(null, false, null);
            while (active != null && active.phase == Phase.RESTORING) {
                active.tickRestore();
            }
            active = null;
        }
    }

    // ------------------------------------------------------------------ arena

    private void flattenColumn(BlockPos column) {
        BlockPos floor = new BlockPos(column.getX(), floorY, column.getZ());
        setTracked(floor, Blocks.SMOOTH_STONE.defaultBlockState());

        for (int dy = 1; dy <= config.bossArenaClearHeight; dy++) {
            BlockPos pos = new BlockPos(column.getX(), floorY + dy, column.getZ());
            if (!level.getBlockState(pos).isAir()) {
                setTracked(pos, Blocks.AIR.defaultBlockState());
            }
        }

        // Two courses of support so nothing opens up underneath.
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos pos = new BlockPos(column.getX(), floorY - dy, column.getZ());
            if (level.getBlockState(pos).isAir()) {
                setTracked(pos, Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
    }

    private void rebuildBarrier(int radius) {
        for (BlockPos pos : ring) {
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        ring.clear();

        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < radius - 0.8 || d > radius + 0.4) {
                    continue;
                }
                for (int dy = 1; dy <= config.bossBarrierHeight; dy++) {
                    BlockPos pos = centre.offset(dx, floorY + dy - centre.getY(), dz);
                    setTracked(pos, barrier);
                    ring.add(pos);
                }
            }
        }
    }

    private void setTracked(BlockPos pos, BlockState state) {
        BlockPos immutable = pos.immutable();
        snapshot.putIfAbsent(immutable, level.getBlockState(immutable));
        level.setBlock(immutable, state, 2);
    }

    // ------------------------------------------------------------------ boss entity

    private void spawnBoss(ServerPlayer player) {
        if (player == null) {
            return;
        }

        Mob spawned = EntityType.VINDICATOR.spawn(level, centre.above(), EntitySpawnReason.EVENT);
        if (spawned == null) {
            return;
        }

        spawned.setCustomName(Component.literal("PHOON").withStyle(ChatFormatting.LIGHT_PURPLE));
        spawned.setCustomNameVisible(true);
        spawned.setPersistenceRequired();
        spawned.setGlowingTag(true);

        setAttribute(spawned, Attributes.MAX_HEALTH, config.bossHealth);
        setAttribute(spawned, Attributes.ATTACK_DAMAGE, config.bossAttackDamage);
        setAttribute(spawned, Attributes.MOVEMENT_SPEED, 0.5);
        setAttribute(spawned, Attributes.FOLLOW_RANGE, 128.0);
        setAttribute(spawned, Attributes.KNOCKBACK_RESISTANCE, 1.0);

        spawned.setHealth(spawned.getMaxHealth());
        spawned.setTarget(player);

        boss = spawned;
    }

    private static void setAttribute(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    // ------------------------------------------------------------------ song

    private void playSong() {
        level.playSound(
                null,
                centre.getX() + 0.5,
                centre.getY() + 0.5,
                centre.getZ() + 0.5,
                ModSounds.PHOON,
                SoundSource.RECORDS,
                config.bossSongVolume,
                1.0F);
        songTimer = config.phoonSongLengthTicks;
    }

    private void stopSong(ServerPlayer player) {
        if (player != null) {
            player.connection.send(new ClientboundStopSoundPacket(
                    Identifier.fromNamespaceAndPath("cs2bhop", "phoon"), SoundSource.RECORDS));
        }
    }

    // ------------------------------------------------------------------ sync

    private void sync(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, BhopPayloads.BossSync.TYPE)) {
            return;
        }

        ServerPlayNetworking.send(
                player,
                new BhopPayloads.BossSync(
                        phase == Phase.FIGHTING, Math.round(bossPoints), Math.round(playerPoints), ticksLeft, tiredTicks > 0));
    }

    public static void clearFor(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, BhopPayloads.BossSync.TYPE)) {
            ServerPlayNetworking.send(player, new BhopPayloads.BossSync(false, 0, 0, 0, false));
        }
    }

    public String playerName() {
        return playerName;
    }
}

