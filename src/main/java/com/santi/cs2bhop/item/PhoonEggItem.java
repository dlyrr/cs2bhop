package com.santi.cs2bhop.item;

import com.santi.cs2bhop.boss.PhoonBossFight;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/** Summons the Phoon boss where you place it. Consumed on use. */
public class PhoonEggItem extends Item {

    public PhoonEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        if (PhoonBossFight.isActive()) {
            player.sendSystemMessage(
                    Component.literal("PHOON is already out there.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        BlockPos centre = context.getClickedPos().above();
        if (PhoonBossFight.start(level, player, centre) == null) {
            return InteractionResult.FAIL;
        }

        context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            java.util.function.Consumer<Component> adder,
            TooltipFlag flag) {
        adder.accept(Component.literal("Place it down and outscore what comes out")
                .withStyle(ChatFormatting.GRAY));
        adder.accept(Component.literal("The ground will be levelled. It will be put back.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
