package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.recording.ActionSettings.ItemRule;
import com.skilles.chronoclones.recording.RecordedItem;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What counts as the item an action was recorded with.
 *
 * <p>A recording keeps the whole item, components included. Whether the components have to match is
 * the routine's business rather than this one's: most of the time "a pickaxe" is what was meant,
 * and insisting on the exact pickaxe would stop the routine the moment the tool took a scratch. For
 * the routines where the components <em>are</em> the point -- which potion, which firework, whether
 * the crossbow is loaded -- the rule says so.
 */
public record ItemMatch(RecordedItem template, ItemRule rule) {

    public static ItemMatch of(RecordedItem template, ItemRule rule) {
        return new ItemMatch(template, rule);
    }

    /** Anything of this kind, which is what everything not carrying a template asks for. */
    public static ItemMatch sameItem(Item item) {
        return new ItemMatch(
                RecordedItem.of(item.builtInRegistryHolder()), ItemRule.SAME_ITEM);
    }

    /** True for an action recorded with nothing in hand, which needs nothing lent to it. */
    public boolean isEmptyHanded() {
        return template.isEmpty();
    }

    public boolean accepts(ItemResource resource) {
        if (resource.getItem() != template.item().value()) {
            return false;
        }
        return switch (rule) {
            case SAME_ITEM -> true;
            // The patch rather than the built stack: two stacks of the same item differ by what has
            // been set on them, and that is exactly the list a template carries.
            case EXACT -> RecordedItem.of(resource.toStack(1)).components()
                    .equals(template.components());
        };
    }
}
