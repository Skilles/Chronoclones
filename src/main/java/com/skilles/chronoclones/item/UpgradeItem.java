package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=26 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import org.jspecify.annotations.NonNull;

public class UpgradeItem extends Item {

    private final String descriptionKey;

    public UpgradeItem(Properties properties, String descriptionKey) {
        super(properties);
        this.descriptionKey = descriptionKey;
    }

    //? if >=26 {
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display,
                                Consumer<Component> adder, @NonNull TooltipFlag flag) {
        appendSharedHoverText(stack, context, adder, flag);
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> lines, TooltipFlag flag) {
        appendSharedHoverText(stack, context, lines::add, flag);
    }
    *///?}

    private void appendSharedHoverText(ItemStack stack, TooltipContext context,
                                       Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        adder.accept(Component.translatable("tooltip.chronoclones.upgrade.slot")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
