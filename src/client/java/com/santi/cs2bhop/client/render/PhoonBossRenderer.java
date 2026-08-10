package com.santi.cs2bhop.client.render;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.santi.cs2bhop.entity.PhoonBossEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders PHOON from the GeckoLib model.
 *
 * <p>{@link DefaultedEntityGeoModel} resolves all three asset paths from the entity id, so the model
 * lives at {@code geo/phoon_boss.geo.json}, animations at {@code animations/phoon_boss.animation.json}
 * and the texture at {@code textures/entity/phoon_boss.png} — replace any of them in Blockbench and
 * the change is picked up on the next resource reload, no code edit.
 */
public class PhoonBossRenderer extends GeoEntityRenderer<PhoonBossEntity, LivingEntityRenderState> {

    public PhoonBossRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Identifier.fromNamespaceAndPath("cs2bhop", "phoon_boss")));
        this.scaleWidth = 1.15F;
        this.scaleHeight = 1.15F;
    }
}
