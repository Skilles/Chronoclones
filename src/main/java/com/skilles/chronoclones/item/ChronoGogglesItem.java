package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Headwear that shows what every anchor around you is going to do.
 *
 * <p>Its own class rather than {@link UpgradeItem} for one word: these are <em>worn</em>, and the
 * upgrade tooltip ends with "Slot into an Chrono Anchor". An item whose tooltip tells you to put it
 * somewhere it does not go is worse than one with no tooltip at all.
 */
public class ChronoGogglesItem extends Item {

    public ChronoGogglesItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("tooltip.chronoclones.chrono_goggles")
                .withStyle(ChatFormatting.GRAY));
    }
}
