package com.santi.cs2bhop.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.santi.cs2bhop.Cs2Bhop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * How the boss moves, as a loopable list of moves rather than hardcoded chase logic.
 *
 * <p>Read from {@code config/cs2bhop_boss_moves.json}, which is what the movement editor in
 * {@code tools/phoon-choreographer.html} exports. Edit it there, drop the file in, restart the
 * fight — no rebuild.
 *
 * <p>Every move steers relative to the player rather than to world coordinates, so a routine still
 * reads the same whether the fight is at spawn or ten thousand blocks out, and still works while
 * the arena shrinks around it.
 */
public final class BossChoreography {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * One step. {@code ticks} is how long it runs, {@code speed} multiplies the boss's base speed,
     * {@code radius} is the distance it tries to hold where the move cares about distance.
     */
    public record Move(String type, int ticks, double speed, double radius) {
        public int ticksOrDefault() {
            return Math.max(1, ticks);
        }

        public double speedOrDefault() {
            return speed <= 0.0 ? 1.0 : speed;
        }
    }

    /** Charge, circle, feint, close, back off, pounce. */
    private static final List<Move> DEFAULT_ROUTINE = List.of(
            new Move("charge", 40, 1.0, 0),
            new Move("orbit", 60, 0.9, 8),
            new Move("charge", 30, 1.3, 0),
            new Move("strafe", 40, 1.0, 6),
            new Move("retreat", 25, 0.8, 14),
            new Move("leap", 20, 1.6, 0));

    private static List<Move> routine = DEFAULT_ROUTINE;
    private static int totalTicks = DEFAULT_ROUTINE.stream().mapToInt(Move::ticksOrDefault).sum();

    private BossChoreography() {}

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cs2bhop_boss_moves.json");
    }

    /** Loads the routine, writing the default out on first run so there is something to edit. */
    public static void load() {
        Path path = path();
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                List<Move> loaded = GSON.fromJson(json, new TypeToken<List<Move>>() {}.getType());
                if (loaded != null && !loaded.isEmpty()) {
                    routine = loaded;
                    totalTicks = routine.stream().mapToInt(Move::ticksOrDefault).sum();
                    Cs2Bhop.LOGGER.info("Loaded {} boss moves ({} ticks)", routine.size(), totalTicks);
                    return;
                }
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(DEFAULT_ROUTINE), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            Cs2Bhop.LOGGER.warn("Could not read {}, using the built-in routine", path, e);
        }

        routine = DEFAULT_ROUTINE;
        totalTicks = DEFAULT_ROUTINE.stream().mapToInt(Move::ticksOrDefault).sum();
    }

    /** The move playing at this point in the routine. */
    public static Move moveAt(int fightTick) {
        if (routine.isEmpty() || totalTicks <= 0) {
            return DEFAULT_ROUTINE.get(0);
        }

        int t = Math.floorMod(fightTick, totalTicks);
        for (Move move : routine) {
            t -= move.ticksOrDefault();
            if (t < 0) {
                return move;
            }
        }
        return routine.get(routine.size() - 1);
    }

    /**
     * Horizontal steering for this tick, in blocks per tick.
     *
     * @param baseSpeed the boss's base speed in blocks per tick
     */
    public static Vec3 steer(Mob boss, Player player, int fightTick, double baseSpeed) {
        Move move = moveAt(fightTick);

        double dx = player.getX() - boss.getX();
        double dz = player.getZ() - boss.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0e-4) {
            return Vec3.ZERO;
        }

        double toX = dx / distance;
        double toZ = dz / distance;
        // Left-hand perpendicular, for anything that goes around rather than at.
        double perpX = -toZ;
        double perpZ = toX;

        double speed = baseSpeed * move.speedOrDefault();
        double wantX;
        double wantZ;

        switch (move.type() == null ? "charge" : move.type().toLowerCase()) {
            case "orbit" -> {
                // Circle at radius, easing in or out to hold it.
                double correction = Math.max(-1.0, Math.min(1.0, (distance - move.radius()) / 6.0));
                wantX = perpX + toX * correction;
                wantZ = perpZ + toZ * correction;
            }
            case "strafe" -> {
                // Weave across the approach: sidestep flipping every half second.
                double side = ((fightTick / 10) % 2 == 0) ? 1.0 : -1.0;
                double correction = Math.max(-1.0, Math.min(1.0, (distance - move.radius()) / 6.0));
                wantX = perpX * side * 0.9 + toX * correction;
                wantZ = perpZ * side * 0.9 + toZ * correction;
            }
            case "retreat" -> {
                double correction = distance < move.radius() ? -1.0 : 0.2;
                wantX = toX * correction;
                wantZ = toZ * correction;
            }
            case "hover" -> {
                wantX = 0.0;
                wantZ = 0.0;
            }
            case "leap", "charge" -> {
                wantX = toX;
                wantZ = toZ;
            }
            default -> {
                wantX = toX;
                wantZ = toZ;
            }
        }

        double length = Math.sqrt(wantX * wantX + wantZ * wantZ);
        if (length < 1.0e-4) {
            return Vec3.ZERO;
        }

        return new Vec3(wantX / length * speed, 0.0, wantZ / length * speed);
    }

    /** Whether this move should also launch the boss upward on the tick it starts. */
    public static boolean isLeap(int fightTick) {
        Move move = moveAt(fightTick);
        return "leap".equalsIgnoreCase(move.type());
    }

    public static List<Move> routine() {
        return routine;
    }
}
