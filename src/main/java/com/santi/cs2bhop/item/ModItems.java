package com.santi.cs2bhop.item;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorType;

public final class ModItems {

    private static final Map<BootTier, BhopBootsItem> BOOTS = new EnumMap<>(BootTier.class);

    public static PhoonEggItem PHOON_EGG;

    private ModItems() {}

    public static BhopBootsItem of(BootTier tier) {
        return BOOTS.get(tier);
    }

    public static void register() {
        for (BootTier tier : BootTier.values()) {
            ResourceKey<Item> key =
                    ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("cs2bhop", tier.id()));

            Item.Properties properties = new Item.Properties()
                    .humanoidArmor(tier.material(), ArmorType.BOOTS)
                    .rarity(tier.secret() ? Rarity.EPIC : Rarity.COMMON)
                    .setId(key);

            if (tier.secret()) {
                properties.fireResistant();
            }

            BhopBootsItem item = Registry.register(BuiltInRegistries.ITEM, key, new BhopBootsItem(tier, properties));
            BOOTS.put(tier, item);
        }

        ResourceKey<Item> eggKey =
                ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("cs2bhop", "phoon_egg"));
        PHOON_EGG = Registry.register(
                BuiltInRegistries.ITEM,
                eggKey,
                new PhoonEggItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).setId(eggKey)));

        // The Phoon Boots are deliberately absent here — they are earned, not browsed.
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            for (BootTier tier : BhopBootsItem.obtainableTiers()) {
                output.insertAfter(Items.NETHERITE_BOOTS, of(tier));
            }
        });
    }
}
