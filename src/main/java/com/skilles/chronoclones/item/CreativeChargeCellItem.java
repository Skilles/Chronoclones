package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

public class CreativeChargeCellItem extends Item {

    public CreativeChargeCellItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(@NonNull ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display,
                                Consumer<Component> adder, @NonNull TooltipFlag flag) {
        adder.accept(Component.translatable("tooltip.chronoclones.charge_cell")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
