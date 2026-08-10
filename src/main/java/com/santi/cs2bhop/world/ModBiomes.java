package com.santi.cs2bhop.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The bhop biome: wide, flat, a scattering of oaks, and 1.5x points for hopping there.
 *
 * <p>The biome itself is a datapack entry ({@code data/cs2bhop/worldgen/biome/bhop_flats.json});
 * this class only holds the key and the lookup. Placement into the overworld happens in
 * {@code OverworldBiomeBuilderMixin}, because Fabric's biome API only offers injection for the
 * Nether and the End.
 *
 * <p>On flatness: terrain shape is a property of the dimension's density functions, not of a biome,
 * so no biome can force the ground flat. What it <i>can</i> do is claim the parameter space where
 * the generator already produces flat ground — erosion band 6 ({@code 0.55..1.0}), which is what
 * vanilla uses for its flattest inland terrain. The result is genuinely flat, but it is flat because
 * of where the biome lives rather than because the biome levelled anything.
 */
public final class ModBiomes {

    public static final ResourceKey<Biome> BHOP_FLATS =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("cs2bhop", "bhop_flats"));

    private ModBiomes() {}

    public static boolean isBhopBiome(Level level, BlockPos pos) {
        return level.getBiome(pos).is(BHOP_FLATS);
    }
}
