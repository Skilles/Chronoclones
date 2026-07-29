package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ActionSettings;
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
        ChronoclonesGameTests.add("each_clone_draws_from_its_own_inventory",
                CloneInventoryGameTest::eachCloneDrawsFromItsOwn);
        ChronoclonesGameTests.add("pulled_splitter_spills_its_clone",
                CloneInventoryGameTest::pulledSplitterSpills);
        ChronoclonesGameTests.add("legacy_inventory_loads_into_the_first_clone",
                CloneInventoryGameTest::legacyInventoryMigrates);
        ChronoclonesGameTests.add("held_slot_is_drawn_from_first",
                CloneInventoryGameTest::heldSlotIsDrawnFromFirst);
        ChronoclonesGameTests.add("held_slot_falls_back_to_a_search",
                CloneInventoryGameTest::heldSlotFallsBackToASearch);
        ChronoclonesGameTests.add("mined_loot_fills_the_hotbar_first",
                CloneInventoryGameTest::minedLootFillsTheHotbarFirst);
        ChronoclonesGameTests.add("exact_slot_rule_refuses_to_search",
                CloneInventoryGameTest::exactSlotRuleRefusesToSearch);
        ChronoclonesGameTests.add("any_slot_rule_ignores_the_recorded_square",
                CloneInventoryGameTest::anySlotRuleIgnoresTheRecordedSquare);
    }

    /** EXACT is for a routine that sorts as it works: the named square or nothing. */
    private static void exactSlotRuleRefusesToSearch(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = placingAnchor(helper,
                new ActionSettings.SlotRule(ActionSettings.SlotRule.Mode.EXACT, 4));
        anchor.getCloneInventory(0).set(17, ItemResource.of(Items.STONE), 1);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (!helper.getBlockState(AnchorTestFixture.targetOf(ANCHOR)).isAir()) {
                        helper.fail("an exact rule went looking outside the square it names");
                    }
                    if (anchor.getLastFailure().reason()
                            != com.skilles.chronoclones.block.DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("expected NO_ITEM, got " + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /** ANY has no square to prefer, so it takes the first stone it finds. */
    private static void anySlotRuleIgnoresTheRecordedSquare(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = placingAnchor(helper,
                new ActionSettings.SlotRule(ActionSettings.SlotRule.Mode.ANY, 4));
        anchor.getCloneInventory(0).set(4, ItemResource.of(Items.STONE), 1);
        anchor.getCloneInventory(0).set(2, ItemResource.of(Items.STONE), 1);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (helper.getBlockState(AnchorTestFixture.targetOf(ANCHOR)).isAir()) {
                        helper.fail("an any rule placed nothing at all");
                        return;
                    }
                    if (!anchor.getCloneInventory(0).getResource(2).isEmpty()) {
                        helper.fail("an any rule still reached for the recorded square first");
                    }
                })
                .thenSucceed();
    }

    /** A clone picks up the way a player does: the first free square, which is the hotbar. */
    private static void minedLootFillsTheHotbarFirst(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    ItemStacksResourceHandler inventory = anchor.getCloneInventory(0);
                    if (AnchorTestFixture.countIn(inventory, Items.COBBLESTONE) == 0) {
                        helper.fail("the clone stored nothing it mined");
                        return;
                    }
                    if (inventory.getResource(0).getItem() != Items.COBBLESTONE) {
                        helper.fail("the cobblestone went past the first hotbar square into "
                                + firstHolding(inventory, Items.COBBLESTONE));
                    }
                })
                .thenSucceed();
    }

    private static int firstHolding(ItemStacksResourceHandler inventory, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getResource(slot).getItem() == item) {
                return slot;
            }
        }
        return -1;
    }

    /** The recorded square is where the clone reaches, not merely somewhere the item is. */
    private static void heldSlotIsDrawnFromFirst(GameTestHelper helper) {
        int recorded = 4;
        ChronoAnchorBlockEntity anchor = placingAnchor(helper, recorded);
        anchor.getCloneInventory(0).set(recorded, ItemResource.of(Items.STONE), 1);
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.STONE), 1);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (!anchor.getCloneInventory(0).getResource(recorded).isEmpty()) {
                        helper.fail("the recorded square still holds its stone; some other was spent");
                    }
                    if (anchor.getCloneInventory(0).getAmountAsInt(0) != 1) {
                        helper.fail("square 0 was raided while the recorded square was full");
                    }
                })
                .thenSucceed();
    }

    /** Stock rarely lands where the recording left it, so the square is a preference. */
    private static void heldSlotFallsBackToASearch(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = placingAnchor(helper, 4);
        anchor.getCloneInventory(0).set(17, ItemResource.of(Items.STONE), 1);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (helper.getBlockState(AnchorTestFixture.targetOf(ANCHOR)).isAir()) {
                        helper.fail("the routine refused stone that was one square over");
                    }
                })
                .thenSucceed();
    }

    /** An anchor whose routine places one stone, recorded as held in {@code heldSlot}. */
    private static ChronoAnchorBlockEntity placingAnchor(GameTestHelper helper, int heldSlot) {
        return placingAnchor(helper, ActionSettings.SlotRule.prefer(heldSlot));
    }

    private static ChronoAnchorBlockEntity placingAnchor(GameTestHelper helper,
                                                         ActionSettings.SlotRule rule) {
        return AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.PlaceBlock(
                        new BlockPos(0, 0, -1), Direction.UP,
                        BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE),
                        Blocks.STONE.defaultBlockState()),
                        ActionSettings.DEFAULT.withSlot(rule)));
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** One clone, stock in the second inventory: it must starve rather than borrow. */
    private static void cannotReachAnotherInventory(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = placingAnchor(helper, 0);
        anchor.getCloneInventory(1).set(0, ItemResource.of(Items.STONE), 16);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    BlockState placed = helper.getBlockState(AnchorTestFixture.targetOf(ANCHOR));
                    if (!placed.isAir()) {
                        helper.fail("the only clone placed " + placed + " out of an inventory it does not own");
                    }
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(1), Items.STONE) != 16) {
                        helper.fail("stock was spent from an inventory no running clone owns");
                    }
                })
                .thenSucceed();
    }

    /** Two clones, stock in the second: the second is the one that can act on it. */
    private static void eachCloneDrawsFromItsOwn(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = placingAnchor(helper, 0);
        splitters(anchor, 1);
        anchor.getCloneInventory(1).set(0, ItemResource.of(Items.STONE), 16);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (helper.getBlockState(AnchorTestFixture.targetOf(ANCHOR)).isAir()) {
                        helper.fail("the second clone never spent the stock it owns");
                    }
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(1), Items.STONE) != 15) {
                        helper.fail("the placed stone came from somewhere other than its owner");
                    }
                    if (!anchor.getCloneInventory(0).getResource(0).isEmpty()) {
                        helper.fail("the first clone's inventory grew stone it was never given");
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
                new AABB(helper.absolutePos(ANCHOR)).inflate(5.0));
        int total = 0;
        for (ItemEntity entity : drops) {
            if (entity.getItem().is(item)) {
                total += entity.getItem().getCount();
            }
        }
        return total;
    }
}
