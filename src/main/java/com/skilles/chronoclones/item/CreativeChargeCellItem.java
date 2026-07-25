package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Creative-only fuel that keeps an anchor permanently charged.
 *
 * <p>Exists to make the charge system observable while testing. Charge is otherwise hard to reason
 * about from inside the game: a routine that stops could be out of charge, out of items, blocked,
 * or hitting a cap, and waiting for a coal block to burn down to tell them apart is a poor way to
 * spend a debugging session.
 *
 * <p>Never consumed, and the anchor tops up from it every tick rather than burning it, so charge
 * effectively stops being a constraint while it is installed.
 */
public class CreativeChargeCellItem extends Item {

    public CreativeChargeCellItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("tooltip.chronoclones.charge_cell")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        adder.accept(Component.translatable("tooltip.chronoclones.charge_cell.creative")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
