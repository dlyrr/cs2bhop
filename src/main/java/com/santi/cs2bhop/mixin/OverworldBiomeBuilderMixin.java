package com.santi.cs2bhop.mixin;

import com.mojang.datafixers.util.Pair;
import com.santi.cs2bhop.world.ModBiomes;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places the bhop biome into overworld generation.
 *
 * <p>Vanilla builds the overworld's multi-noise parameter list here, so appending our own parameter
 * point is enough — no world type, dimension or datapack override needed.
 *
 * <p>The parameters are chosen for flat ground: erosion band 6 ({@code 0.55..1.0}) is vanilla's
 * flattest, and inland continentalness keeps it off the coast. Weirdness is deliberately a narrow
 * slice so the biome shows up as occasional wide plains rather than taking over the temperate band.
 */
@Mixin(OverworldBiomeBuilder.class)
public class OverworldBiomeBuilderMixin {

    @Inject(method = "addBiomes", at = @At("TAIL"))
    private void cs2bhop$addBhopFlats(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, CallbackInfo ci) {

        Climate.Parameter temperature = Climate.Parameter.span(-0.15F, 0.55F);
        Climate.Parameter humidity = Climate.Parameter.span(-0.1F, 0.3F);
        Climate.Parameter continentalness = Climate.Parameter.span(0.03F, 0.55F);
        Climate.Parameter erosion = Climate.Parameter.span(0.55F, 1.0F);
        Climate.Parameter weirdness = Climate.Parameter.span(-0.2F, 0.2F);

        // Surface only: both depth points, matching vanilla's addSurfaceBiome.
        for (float depth : new float[] {0.0F, 1.0F}) {
            biomes.accept(Pair.of(
                    Climate.parameters(
                            temperature, humidity, continentalness, erosion, Climate.Parameter.point(depth), weirdness, 0.0F),
                    ModBiomes.BHOP_FLATS));
        }
    }
}
