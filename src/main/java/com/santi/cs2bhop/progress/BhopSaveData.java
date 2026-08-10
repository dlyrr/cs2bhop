package com.santi.cs2bhop.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every player's progress, stored on the overworld.
 *
 * <p>This is world data rather than player data on purpose: the leaderboard has to include players
 * who are currently offline, and per-entity attachments only exist while the player is loaded.
 */
public class BhopSaveData extends SavedData {

    public static final Codec<BhopSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerProgress.CODEC)
                            .optionalFieldOf("players", Map.of())
                            .forGetter(data -> data.players),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
                            .optionalFieldOf("names", Map.of())
                            .forGetter(data -> data.names))
            .apply(instance, BhopSaveData::new));

    public static final SavedDataType<BhopSaveData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("cs2bhop", "progress"), BhopSaveData::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, PlayerProgress> players = new HashMap<>();

    /** Cached display names so the leaderboard can list offline players. */
    private final Map<UUID, String> names = new HashMap<>();

    public BhopSaveData() {}

    private BhopSaveData(Map<UUID, PlayerProgress> players, Map<UUID, String> names) {
        this.players.putAll(players);
        this.names.putAll(names);
    }

    public static BhopSaveData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerProgress progress(UUID id) {
        return players.getOrDefault(id, PlayerProgress.EMPTY);
    }

    public void put(UUID id, String name, PlayerProgress progress) {
        players.put(id, progress);
        names.put(id, name);
        setDirty();
    }

    public String nameOf(UUID id) {
        return names.getOrDefault(id, id.toString().substring(0, 8));
    }

    /** Highest points first. */
    public List<Entry> leaderboard(int limit) {
        List<Entry> entries = new ArrayList<>(players.size());
        players.forEach((id, progress) -> entries.add(new Entry(id, nameOf(id), progress)));
        entries.sort(Comparator.comparingLong((Entry e) -> e.progress().points()).reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    public int rankOf(UUID id) {
        long points = progress(id).points();
        int better = 0;
        for (PlayerProgress other : players.values()) {
            if (other.points() > points) {
                better++;
            }
        }
        return better + 1;
    }

    public int trackedPlayers() {
        return players.size();
    }

    public record Entry(UUID id, String name, PlayerProgress progress) {}
}
