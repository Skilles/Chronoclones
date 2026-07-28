package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * That a clone reaches its own inventory and no other.
 */
final class CloneInventoryGameTest {

    private CloneInventoryGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("clone_cannot_reach_another_inventory",
                CloneInventoryGameTest::cannotReachAnotherInventory);
        ChronoclonesGameTests.add("drops_go_to_the_clone_that_mined",
                CloneInventoryGameTest::dropsGoToTheMiner);
        ChronoclonesGameTests.add("pulled_splitter_spills_its_clone",
                CloneInventoryGameTest::pulledSplitterSpills);
        ChronoclonesGameTests.add("legacy_inventory_loads_into_the_first_clone",
                CloneInventoryGameTest::legacyInventoryMigrates);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** Stock only the second clone: the first must starve rather than borrow. */
    private static void cannotReachAnotherInventory(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.PlaceBlock(
                        new BlockPos(0, 0, -1), Direction.UP,
                        BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE),
                        Blocks.STONE.defaultBlockState())));
        AnchorTestFixture.unlockAllActions(anchor);
        splitters(anchor, 1);

        anchor.getCloneInventory(1).set(0, ItemResource.of(Items.STONE), 16);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    BlockState placed = helper.getBlockState(AnchorTestFixture.targetOf(ANCHOR));
                    if (!placed.isAir()) {
                        helper.fail("clone 0 placed " + placed + " out of clone 1's inventory");
                    }
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(1), Items.STONE) != 16) {
                        helper.fail("clone 1's stone was spent by a clone that does not own it");
                    }
                })
                .thenSucceed();
    }

    /** Two clones share a routine but not a pocket. */
    private static void dropsGoToTheMiner(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        splitters(anchor, 1);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    int first = AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.COBBLESTONE);
                    int second = AnchorTestFixture.countIn(anchor.getCloneInventory(1), Items.COBBLESTONE);
                    if (first + second == 0) {
                        helper.fail("neither clone stored what it mined");
                    }
                    if (first > 0 && second > 0) {
                        helper.fail("one block broken but both clones were paid for it");
                    }
                })
                .thenSucceed();
    }

    /** Pulling the splitter is not a way to make a clone's inventory disappear. */
    private static void pulledSplitterSpills(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        splitters(anchor, 1);
        anchor.getCloneInventory(1).set(0, ItemResource.of(Items.DIAMOND), 5);

        helper.startSequence()
                // Long enough for the anchor to have noticed the splitter before it goes away.
                .thenExecuteAfter(4, () -> anchor.getUpgradeHandler().set(0, ItemResource.EMPTY, 0))
                .thenExecuteAfter(4, () -> {
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(1), Items.DIAMOND) != 0) {
                        helper.fail("the dropped clone kept its inventory, out of reach of the GUI");
                    }
                    if (droppedCount(helper, Items.DIAMOND) != 5) {
                        helper.fail("expected 5 diamonds on the ground, found "
                                + droppedCount(helper, Items.DIAMOND));
                    }
                })
                .thenSucceed();
    }

    /** Anchors saved before clones had inventories of their own must not lose what they held. */
    private static void legacyInventoryMigrates(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        ServerLevel level = helper.getLevel();

        anchor.loadWithComponents(TagValueInput.create(
                ProblemReporter.DISCARDING, level.registryAccess(), legacySave(level)));

        ItemStacksResourceHandler first = anchor.getCloneInventory(0);
        if (first.size() != ChronoAnchorBlockEntity.CLONE_INVENTORY_SLOTS) {
            helper.fail("loading an old anchor shrank its inventory to " + first.size() + " slots");
        }
        if (AnchorTestFixture.countIn(first, Items.DIAMOND) != 9) {
            helper.fail("expected 9 diamonds in the first clone, found "
                    + AnchorTestFixture.countIn(first, Items.DIAMOND));
        }
        helper.succeed();
    }

    /** The anchor tag as it was written before clone inventories: one 18-slot {@code inventory}. */
    private static CompoundTag legacySave(ServerLevel level) {
        ItemStacksResourceHandler legacy = new ItemStacksResourceHandler(18);
        legacy.set(4, ItemResource.of(Items.DIAMOND), 9);

        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING, level.registryAccess());
        legacy.serialize(output.child("inventory"));
        return output.buildResult();
    }

    private static void splitters(ChronoAnchorBlockEntity anchor, int count) {
        anchor.getUpgradeHandler().set(0, ItemResource.of(ModItems.CHRONO_SPLITTER.get()), count);
    }

    private static int droppedCount(GameTestHelper helper, net.minecraft.world.item.Item item) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(ANCHOR)).inflate(2.0));
        int total = 0;
        for (ItemEntity entity : drops) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }
}
