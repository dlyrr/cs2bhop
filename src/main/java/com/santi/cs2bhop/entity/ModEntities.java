package com.santi.cs2bhop.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {

    public static EntityType<PhoonBossEntity> PHOON_BOSS;

    private ModEntities() {}

    public static void register() {
        ResourceKey<EntityType<?>> key =
                ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("cs2bhop", "phoon_boss"));

        PHOON_BOSS = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                key,
                EntityType.Builder.of(PhoonBossEntity::new, MobCategory.MONSTER)
                        .sized(0.7F, 2.2F)
                        .fireImmune()
                        .clientTrackingRange(16)
                        .build(key));

        FabricDefaultAttributeRegistry.register(PHOON_BOSS, PhoonBossEntity.createAttributes());
    }
}
