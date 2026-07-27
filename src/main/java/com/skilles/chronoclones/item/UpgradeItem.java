package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;

/**
 * An anchor upgrade, which is only ever an item in a slot.
 */
public class UpgradeItem extends Item {

    private final String descriptionKey;

    public UpgradeItem(Properties properties, String descriptionKey) {
        super(properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display,
                                Consumer<Component> adder, @NonNull TooltipFlag flag) {
        adder.accept(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        adder.accept(Component.translatable("tooltip.chronoclones.upgrade.slot")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
