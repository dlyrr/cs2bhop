package com.santi.cs2bhop.item;

import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;

/**
 * The six bhop boots.
 *
 * <p>{@code damagePerHop} is what one banked hop is worth when you release the shockwave, so the
 * boots reward chaining rather than just wearing them. {@code pointMultiplier} applies to every
 * point you earn while they are on.
 *
 * <p>Vanilla armour materials are reused so the boots render correctly when worn without needing
 * custom armour-layer textures; only the inventory icon is ours.
 */
public enum BootTier {
    //                                  dmg   points  base  /hop   cap
    WOOD("wooden_bhop_boots", 1.0F, 1.10, 4.0, 0.30, 10.0, ArmorMaterials.LEATHER, false),
    COPPER("copper_bhop_boots", 2.0F, 1.25, 5.0, 0.35, 12.0, ArmorMaterials.COPPER, false),
    IRON("iron_bhop_boots", 5.0F, 1.50, 6.0, 0.40, 16.0, ArmorMaterials.IRON, false),
    DIAMOND("diamond_bhop_boots", 6.0F, 1.75, 7.0, 0.45, 18.0, ArmorMaterials.DIAMOND, false),
    NETHERITE("netherite_bhop_boots", 8.0F, 2.00, 8.0, 0.50, 22.0, ArmorMaterials.NETHERITE, false),
    PHOON("phoon_boots", 10.0F, 3.00, 10.0, 0.60, 28.0, ArmorMaterials.NETHERITE, true);

    private final String id;
    private final float damagePerHop;
    private final double pointMultiplier;
    private final double baseRadius;
    private final double radiusPerHop;
    private final double maxRadius;
    private final ArmorMaterial material;
    private final boolean secret;

    BootTier(
            String id,
            float damagePerHop,
            double pointMultiplier,
            double baseRadius,
            double radiusPerHop,
            double maxRadius,
            ArmorMaterial material,
            boolean secret) {
        this.id = id;
        this.damagePerHop = damagePerHop;
        this.pointMultiplier = pointMultiplier;
        this.baseRadius = baseRadius;
        this.radiusPerHop = radiusPerHop;
        this.maxRadius = maxRadius;
        this.material = material;
        this.secret = secret;
    }

    public String id() {
        return id;
    }

    public float damagePerHop() {
        return damagePerHop;
    }

    public double pointMultiplier() {
        return pointMultiplier;
    }

    public ArmorMaterial material() {
        return material;
    }

    /** Secret boots are not in the creative tab and have no recipe; they have to be earned. */
    public boolean secret() {
        return secret;
    }

    /** Total shockwave damage for a banked hop count. */
    public float shockwaveDamage(int hops) {
        return damagePerHop * hops;
    }

    public double baseRadius() {
        return baseRadius;
    }

    public double maxRadius() {
        return maxRadius;
    }

    /**
     * Shockwave radius in blocks. Every tier gets its own footprint — better boots hit harder
     * <i>and</i> wider — growing with the chain so a long run is worth holding, then capping so it
     * never quietly turns into a chunk-wide mob wipe.
     */
    public double shockwaveRadius(int hops) {
        return Math.min(maxRadius, baseRadius + hops * radiusPerHop);
    }
}
