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

public class CreativeChargeCellItem extends Item {

    public CreativeChargeCellItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(@NonNull ItemStack stack) {
        return true;
    }

    //? if >=26 {
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display,
                                Consumer<Component> adder, @NonNull TooltipFlag flag) {
        appendSharedHoverText(stack, adder, flag);
    }
    //?} else {
    //? if >=1.20.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> lines, TooltipFlag flag) {
        appendSharedHoverText(stack, lines::add, flag);
    }
    *///?} else {
    /*@Override
    public void appendHoverText(ItemStack stack,
                                net.minecraft.world.level.@org.jspecify.annotations.Nullable Level level,
                                java.util.List<Component> lines, TooltipFlag flag) {
        appendSharedHoverText(stack, lines::add, flag);
    }
    *///?}
    //?}

    private void appendSharedHoverText(ItemStack stack,
                                       Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("tooltip.chronoclones.charge_cell")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
