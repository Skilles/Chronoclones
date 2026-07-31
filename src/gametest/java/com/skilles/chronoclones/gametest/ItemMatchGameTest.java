package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.recording.ActionSettings.ItemRule;
import com.skilles.chronoclones.recording.RecordedItem;
import com.skilles.chronoclones.replay.ItemMatch;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * How closely an action has to match the item it was recorded with.
 *
 * <p>A game test rather than a unit test because it is about real stacks, and the unit source set
 * never binds item component prototypes.
 */
final class ItemMatchGameTest {

    private ItemMatchGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("item_match_same_kind_is_the_default",
                ItemMatchGameTest::sameKindIsTheDefault);
        ChronoclonesGameTests.add("item_match_exact_wants_the_components",
                ItemMatchGameTest::exactWantsTheComponents);
    }

    /** By default a routine recorded with a worn tool is happy with a fresh one. */
    private static void sameKindIsTheDefault(GameTestHelper helper) {
        ItemMatch match = ItemMatch.of(RecordedItem.of(damaged(Items.DIAMOND_HOE, 7)),
                ItemRule.SAME_ITEM);

        if (!match.accepts(ItemResource.of(new ItemStack(Items.DIAMOND_HOE)))) {
            helper.fail("a routine recorded with a scratched hoe refused an unscratched one");
        }
        if (match.accepts(ItemResource.of(new ItemStack(Items.IRON_HOE)))) {
            helper.fail("an iron hoe satisfied a routine recorded with a diamond one");
        }
        helper.succeed();
    }

    /** Asked for exactly, what is on the item has to agree as well. */
    private static void exactWantsTheComponents(GameTestHelper helper) {
        ItemMatch match = ItemMatch.of(RecordedItem.of(damaged(Items.DIAMOND_HOE, 7)),
                ItemRule.EXACT);

        if (!match.accepts(ItemResource.of(damaged(Items.DIAMOND_HOE, 7)))) {
            helper.fail("an exact rule refused the very item it was recorded with");
        }
        if (match.accepts(ItemResource.of(new ItemStack(Items.DIAMOND_HOE)))) {
            helper.fail("an exact rule accepted an item carrying different components");
        }
        helper.succeed();
    }

    private static ItemStack damaged(Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DAMAGE, damage);
        return stack;
    }
}
