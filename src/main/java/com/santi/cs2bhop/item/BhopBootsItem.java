package com.santi.cs2bhop.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

/** Boots that carry a {@link BootTier}. */
public class BhopBootsItem extends Item {

    private final BootTier tier;

    public BhopBootsItem(BootTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public BootTier tier() {
        return tier;
    }

    /** The tier of whatever is on the entity's feet, or null. */
    public static @Nullable BootTier wornBy(LivingEntity entity) {
        ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
        return boots.getItem() instanceof BhopBootsItem item ? item.tier() : null;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            java.util.function.Consumer<Component> adder,
            TooltipFlag flag) {
        adder.accept(Component.literal("%.0f damage per banked hop".formatted(tier.damagePerHop()))
                .withStyle(ChatFormatting.GRAY));
        adder.accept(Component.literal("%.2fx bhop points".formatted(tier.pointMultiplier()))
                .withStyle(ChatFormatting.GRAY));
        adder.accept(Component.literal("Press the boot ability key to release the chain")
                .withStyle(ChatFormatting.DARK_GRAY));

        if (tier == BootTier.PHOON) {
            adder.accept(Component.literal("They know.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Every non-secret tier, for the creative tab and recipe generation. */
    public static List<BootTier> obtainableTiers() {
        return List.of(BootTier.WOOD, BootTier.COPPER, BootTier.IRON, BootTier.DIAMOND, BootTier.NETHERITE);
    }
}
