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
    WOOD("wooden_bhop_boots", 1.0F, 1.10, ArmorMaterials.LEATHER, false),
    COPPER("copper_bhop_boots", 2.0F, 1.25, ArmorMaterials.COPPER, false),
    IRON("iron_bhop_boots", 5.0F, 1.50, ArmorMaterials.IRON, false),
    DIAMOND("diamond_bhop_boots", 6.0F, 1.75, ArmorMaterials.DIAMOND, false),
    NETHERITE("netherite_bhop_boots", 8.0F, 2.00, ArmorMaterials.NETHERITE, false),
    PHOON("phoon_boots", 10.0F, 3.00, ArmorMaterials.NETHERITE, true);

    private final String id;
    private final float damagePerHop;
    private final double pointMultiplier;
    private final ArmorMaterial material;
    private final boolean secret;

    BootTier(String id, float damagePerHop, double pointMultiplier, ArmorMaterial material, boolean secret) {
        this.id = id;
        this.damagePerHop = damagePerHop;
        this.pointMultiplier = pointMultiplier;
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

    /**
     * Shockwave radius in blocks. Grows with the chain so a long run is worth holding, but flattens
     * out so it never covers the whole chunk.
     */
    public double shockwaveRadius(int hops) {
        return Math.min(12.0, 3.0 + hops * 0.25);
    }
}
