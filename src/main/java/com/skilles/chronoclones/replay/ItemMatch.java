package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.recording.ActionSettings.ItemRule;
import com.skilles.chronoclones.recording.RecordedItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** How closely the item an action reaches for has to match the one recorded. */
public record ItemMatch(RecordedItem template, ItemRule rule) {

    public static ItemMatch of(RecordedItem template, ItemRule rule) {
        return new ItemMatch(template, rule);
    }

    public static ItemMatch sameItem(Item item) {
        return new ItemMatch(
                RecordedItem.of(item.builtInRegistryHolder()), ItemRule.SAME_ITEM);
    }

    public boolean isEmptyHanded() {
        return template.isEmpty();
    }

    public boolean accepts(ItemStack stack) {
        if (stack.getItem() != template.item().value()) {
            return false;
        }
        return switch (rule) {
            case SAME_ITEM -> true;
            case EXACT -> RecordedItem.of(stack.copyWithCount(1)).components()
                    .equals(template.components());
        };
    }
}
