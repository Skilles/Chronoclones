package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.registry.ModDataComponents;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What survives an anchor being broken.
 */
final class AnchorDropsGameTest {

    private AnchorDropsGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("anchor_drops_with_routine", AnchorDropsGameTest::dropsWithRoutine);
        ChronoclonesGameTests.add("broken_anchor_spills_inventory", AnchorDropsGameTest::spillsInventory);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private static final double SEARCH_RADIUS = 5.0;

    /** Long enough for the drops to have been added to the level and come to rest. */
    private static final int SETTLE_TICKS = 8;

    /** A routine must not be destroyable with a pickaxe (loot table {@code copy_components}). */
    private static void dropsWithRoutine(GameTestHelper helper) {
        Recording original = AnchorTestFixture.breakOneBlock(Blocks.STONE);
        AnchorTestFixture.placeAndImprint(helper, ANCHOR, original);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(ANCHOR);
        level.destroyBlock(absolute, true);

        helper.startSequence()
                .thenExecuteAfter(SETTLE_TICKS, () -> {
                    ItemStack dropped = findDrop(level, absolute, ModItems.CHRONO_ANCHOR.get().asItem());
                    if (dropped == null) {
                        helper.fail("breaking the anchor dropped no anchor at all");
                        return;
                    }

                    Recording carried = dropped.get(ModDataComponents.RECORDING.get());
                    if (carried == null) {
                        helper.fail("the anchor dropped blank - its routine was destroyed with it");
                        return;
                    }
                    if (!AnchorTestFixture.AUTHOR_ID.equals(carried.authorId())) {
                        helper.fail("the dropped routine changed author to " + carried.authorId());
                    }
                    if (carried.actions().size() != original.actions().size()) {
                        helper.fail("the dropped routine has " + carried.actions().size()
                                + " actions, expected " + original.actions().size());
                    }
                })
                .thenSucceed();
    }

    /** The anchor stores through the transfer API, so the vanilla spill does not see it. */
    private static void spillsInventory(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.DIAMOND), 7);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(ANCHOR);
        level.destroyBlock(absolute, true);

        helper.startSequence()
                .thenExecuteAfter(SETTLE_TICKS, () -> {
                    ItemStack dropped = findDrop(level, absolute, Items.DIAMOND);
                    if (dropped == null) {
                        helper.fail("the anchor's stored items vanished when it was broken");
                        return;
                    }
                    if (dropped.getCount() != 7) {
                        helper.fail("expected 7 diamonds back, got " + dropped.getCount());
                    }
                })
                .thenSucceed();
    }

    /**
     * Scoped to a box around the anchor, since game tests share one world.
     *
     * <p>Wide enough for the scatter: a spilled stack is thrown with random motion, and four
     * inventories now spill at once, which is what made two blocks an intermittent miss.
     */
    private static ItemStack findDrop(ServerLevel level, BlockPos absolute, net.minecraft.world.item.Item item) {
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(absolute).inflate(SEARCH_RADIUS));
        for (ItemEntity entity : drops) {
            if (entity.getItem().is(item)) {
                return entity.getItem();
            }
        }
        return null;
    }
}
