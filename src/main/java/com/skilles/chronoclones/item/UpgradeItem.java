package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An anchor upgrade, which is only ever an item in a slot.
 *
 * <p>Exists purely so each upgrade can say what it does. Four visually similar trinkets whose effect
 * is invisible until slotted is exactly the situation where a player slots all four, sees no change,
 * and concludes the mod is broken — so the tooltip is the feature, not decoration.
 */
public class UpgradeItem extends Item {

    private final String descriptionKey;

    public UpgradeItem(Properties properties, String descriptionKey) {
        super(properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable(descriptionKey).withStyle(ChatFormatting.GRAY));
        adder.accept(Component.translatable("tooltip.chronoclones.upgrade.slot")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
