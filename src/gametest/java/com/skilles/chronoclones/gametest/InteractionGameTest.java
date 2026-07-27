package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ChronoAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The generic interaction paths.
 */
final class InteractionGameTest {

    private InteractionGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("use_on_block_flips_a_lever", InteractionGameTest::flipsLever);
        ChronoclonesGameTests.add("use_needs_its_item_in_the_anchor", InteractionGameTest::needsItsItem);
        ChronoclonesGameTests.add("use_returns_what_it_borrowed", InteractionGameTest::returnsWhatItBorrowed);
        ChronoclonesGameTests.add("container_splits_a_stack_by_intent", InteractionGameTest::splitsByIntent);
        ChronoclonesGameTests.add("container_loads_a_furnace", InteractionGameTest::loadsAFurnace);
        ChronoclonesGameTests.add("container_shift_clicks_out", InteractionGameTest::shiftClicksOut);
        ChronoclonesGameTests.add("container_moves_within_itself", InteractionGameTest::movesWithinContainer);
        ChronoclonesGameTests.add("container_refuses_another_menu", InteractionGameTest::refusesAnotherMenu);
        ChronoclonesGameTests.add("container_deposits_into_a_container", InteractionGameTest::depositsIntoAContainer);
        ChronoclonesGameTests.add("container_full_slot_leaves_the_item_alone",
                InteractionGameTest::fullSlotLeavesTheItemAlone);
        ChronoclonesGameTests.add("container_failed_staging_keeps_the_inventory",
                InteractionGameTest::failedStagingKeepsTheInventory);
        ChronoclonesGameTests.add("container_needs_its_carried_items", InteractionGameTest::needsItsCarriedItems);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /**
     * Nothing in this mod knows what a lever is.
     */
    private static void flipsLever(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target.below(), Blocks.STONE);
        helper.setBlock(target, Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, false));

        AnchorTestFixture.unlockAllActions(AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(useOnBlock(new BlockPos(0, 0, -1), Items.AIR))));

        // One pass, not a loop: a lever is a toggle and the routine repeats.
        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (!helper.getBlockState(target).getValue(LeverBlock.POWERED)) {
                        helper.fail("the routine right-clicked a lever and it did not flip");
                    }
                })
                .thenSucceed();
    }

    /** A routine recorded holding something does not run in an anchor that has none of it. */
    private static void needsItsItem(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(useOnBlock(new BlockPos(0, 0, -1), Items.BONE_MEAL)));
        AnchorTestFixture.unlockAllActions(anchor);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("expected NO_ITEM for a routine whose item is not stocked, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /**
     * The borrowed stack comes home, carrying whatever the interaction did to it.
     */
    private static void returnsWhatItBorrowed(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(useOnBlock(new BlockPos(0, 0, -1), Items.FLINT_AND_STEEL)));
        AnchorTestFixture.unlockAllActions(anchor);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.FLINT_AND_STEEL), 1);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertBlockPresent(Blocks.FIRE, target.above());

                    ItemStack returned = findStack(anchor.getInventory(), Items.FLINT_AND_STEEL);
                    if (returned == null) {
                        helper.fail("the flint and steel lit a fire and never came back - the loan "
                                + "did not return what the interaction left in hand");
                        return;
                    }
                    if (returned.getDamageValue() != 1) {
                        helper.fail("expected the returned flint and steel to carry 1 damage, got "
                                + returned.getDamageValue() + " - the item came back, but not the "
                                + "state the interaction left it in");
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ containers

    /**
     * The reason container work is recorded as clicks rather than amounts.
     */
    private static void splitsByIntent(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);
        stock(level, absoluteTarget, 0, Items.COBBLESTONE, 40);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        // Right-click slot 0: half of it onto the cursor.
                        click(0, RIGHT, ContainerInput.PICKUP),
                        // Left-click the carrier's first slot: put all of it down.
                        click(CHEST_CARRIER_SLOT, LEFT, ContainerInput.PICKUP))));
        AnchorTestFixture.unlockAllActions(anchor);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    int taken = countIn(anchor.getInventory(), Items.COBBLESTONE);
                    if (taken != 20) {
                        helper.fail("expected half of 40 = 20 cobblestone taken, got " + taken
                                + " - a recorded amount was replayed instead of the right-click");
                    }
                })
                .thenSucceed();
    }

    /**
     * Loading a furnace, which is where slots carry meaning.
     */
    private static void loadsAFurnace(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.FURNACE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(FURNACE_MENU_SIZE,
                        List.of(carrying(FURNACE_CARRIER_SLOT, Items.OAK_LOG, 2)),
                        click(FURNACE_CARRIER_SLOT, LEFT, ContainerInput.PICKUP),
                        click(FURNACE_INPUT, RIGHT, ContainerInput.PICKUP),
                        click(FURNACE_FUEL, RIGHT, ContainerInput.PICKUP))));
        AnchorTestFixture.unlockAllActions(anchor);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.OAK_LOG), 2);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    ResourceHandler<ItemResource> furnace =
                            level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
                    if (furnace == null) {
                        helper.fail("the furnace exposes no item handler");
                        return;
                    }
                    assertSlotHolds(helper, furnace, FURNACE_INPUT, Items.OAK_LOG, "input");

                    // A furnace consumes fuel on the tick it arrives, so an empty fuel slot
                    // here means the log got there and burned.
                    if (!helper.getBlockState(target).getValue(BlockStateProperties.LIT)) {
                        helper.fail("the furnace never lit - the second log did not reach the fuel "
                                + "slot, so nothing smelts");
                    }
                })
                .thenSucceed();
    }

    /** Shift-clicking a stack out of a container, routed entirely by the menu's own logic. */
    private static void shiftClicksOut(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        stock(level, helper.absolutePos(target), 0, Items.DIAMOND, 5);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        click(0, LEFT, ContainerInput.QUICK_MOVE))));
        AnchorTestFixture.unlockAllActions(anchor);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (countIn(anchor.getInventory(), Items.DIAMOND) != 5) {
                        helper.fail("expected 5 diamonds shift-clicked into the anchor, got "
                                + countIn(anchor.getInventory(), Items.DIAMOND));
                    }
                })
                .thenSucceed();
    }

    /** Slot to slot inside one container, with the anchor never holding the items. */
    private static void movesWithinContainer(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);
        stock(level, absoluteTarget, 3, Items.COBBLESTONE, 16);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        click(3, LEFT, ContainerInput.PICKUP),
                        click(7, LEFT, ContainerInput.PICKUP))));
        AnchorTestFixture.unlockAllActions(anchor);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    assertSlotHolds(helper, barrel, 7, Items.COBBLESTONE, "destination");
                    if (!barrel.getResource(3).isEmpty()) {
                        helper.fail("slot 3 still holds items - the move did not come out of it");
                    }
                    if (countIn(anchor.getInventory(), Items.COBBLESTONE) != 0) {
                        helper.fail("the anchor ended up holding the items - a slot-to-slot move "
                                + "must not route through the carrier");
                    }
                })
                .thenSucceed();
    }

    /**
     * A session refuses a menu of a different shape.
     */
    private static void refusesAnotherMenu(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.FURNACE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        click(0, LEFT, ContainerInput.QUICK_MOVE))));
        AnchorTestFixture.unlockAllActions(anchor);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("expected WRONG_BLOCK for a chest routine run against a furnace, "
                                + "got " + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }


    /**
     * Depositing, which is the case the carrier layout exists for.
     */
    private static void depositsIntoAContainer(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int carrierSlot = CHEST_MAIN_INVENTORY_START + 4;

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        List.of(carrying(carrierSlot, Items.DIAMOND, 5)),
                        click(carrierSlot, LEFT, ContainerInput.QUICK_MOVE))));
        AnchorTestFixture.unlockAllActions(anchor);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.DIAMOND), 5);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (countIn(barrel, Items.DIAMOND) != 5) {
                        helper.fail("expected 5 diamonds deposited, barrel holds "
                                + countIn(barrel, Items.DIAMOND)
                                + " - the anchor's stock was not staged into the slot the click names");
                    }
                    if (countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                        helper.fail("the anchor kept the diamonds it was supposed to deposit");
                    }
                })
                .thenSucceed();
    }

    /** A session that needs something the anchor has none of says so, rather than clicking air. */
    private static void needsItsCarriedItems(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        List.of(carrying(CHEST_MAIN_INVENTORY_START, Items.DIAMOND, 5)),
                        click(CHEST_MAIN_INVENTORY_START, LEFT, ContainerInput.QUICK_MOVE))));
        AnchorTestFixture.unlockAllActions(anchor);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("expected NO_ITEM for a session whose items are not stocked, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ menu geometry

    // Vanilla menu order: container slots, main inventory, hotbar.
    private static final int CHEST_MENU_SIZE = 27 + 36;
    private static final int CHEST_CARRIER_SLOT = 27 + 27;
    private static final int CHEST_MAIN_INVENTORY_START = 27;
    private static final int FURNACE_MENU_SIZE = 3 + 36;
    private static final int FURNACE_CARRIER_SLOT = 3 + 27;
    private static final int FURNACE_INPUT = 0;
    private static final int FURNACE_FUEL = 1;

    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    /**
     * A click on a full slot does nothing, and does nothing anywhere else either.
     */
    private static void fullSlotLeavesTheItemAlone(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.FURNACE);
        // A full fuel slot with nothing to smelt just sits there, so it stays full for the test.
        AnchorTestFixture.fillSlot(helper, target, FURNACE_FUEL, new ItemStack(Items.COAL, 64));

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(FURNACE_MENU_SIZE,
                        List.of(carrying(FURNACE_CARRIER_SLOT, Items.COAL, 1)),
                        click(FURNACE_CARRIER_SLOT, LEFT, ContainerInput.PICKUP),
                        click(FURNACE_FUEL, LEFT, ContainerInput.PICKUP))));
        AnchorTestFixture.unlockAllActions(anchor);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.COAL), 1);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    ResourceHandler<ItemResource> furnace =
                            level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
                    if (furnace == null) {
                        helper.fail("the furnace exposes no item handler");
                        return;
                    }
                    if (!furnace.getResource(FURNACE_INPUT).isEmpty()) {
                        helper.fail("coal reached the smelting slot: the click went somewhere other "
                                + "than the square it named, got "
                                + furnace.getResource(FURNACE_INPUT).getItem());
                    }
                    // And it is not lost: a click with nowhere to go returns its item.
                    if (countIn(anchor.getInventory(), Items.COAL) != 1) {
                        helper.fail("the coal went nowhere and was not returned to the anchor");
                    }
                })
                .thenSucceed();
    }

    /**
     * A session that cannot be stocked leaves the anchor's contents alone.
     */
    private static void failedStagingKeepsTheInventory(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        List.of(carrying(CHEST_MAIN_INVENTORY_START, Items.DIAMOND, 5)),
                        click(CHEST_MAIN_INVENTORY_START, LEFT, ContainerInput.QUICK_MOVE))));
        AnchorTestFixture.unlockAllActions(anchor);

        // Stocked with something else entirely, so the layout cannot be satisfied.
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.GOLD_INGOT), 12);
        anchor.getInventoryHandler().set(1, ItemResource.of(Items.IRON_INGOT), 7);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    int gold = countIn(anchor.getInventory(), Items.GOLD_INGOT);
                    int iron = countIn(anchor.getInventory(), Items.IRON_INGOT);
                    if (gold != 12 || iron != 7) {
                        helper.fail("a session that could not be stocked ate the anchor's inventory: "
                                + gold + " gold and " + iron + " iron left of 12 and 7");
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ helpers

    private static ChronoAction.UseContainer.Click click(int slot, int button, ContainerInput input) {
        return new ChronoAction.UseContainer.Click(slot, button, input);
    }

    private static ChronoAction session(int menuSize, ChronoAction.UseContainer.Click... clicks) {
        return session(menuSize, List.of(), clicks);
    }

    private static ChronoAction session(int menuSize, List<ChronoAction.UseContainer.CarrierSlot> carrier,
                                      ChronoAction.UseContainer.Click... clicks) {
        return new ChronoAction.UseContainer(new BlockPos(0, 0, -1), menuSize, carrier, List.of(clicks));
    }

    private static ChronoAction.UseContainer.CarrierSlot carrying(int menuSlot, Item item, int count) {
        return new ChronoAction.UseContainer.CarrierSlot(menuSlot, new ItemStack(item, count));
    }

    /** Right-click the top face, dead centre - the geometry a player clicking a floor block produces. */
    private static ChronoAction useOnBlock(BlockPos localPos, Item item) {
        return new ChronoAction.UseOnBlock(localPos, Direction.UP, new Vec3(0.0, 0.5, 0.0), false,
                InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(item));
    }

    private static void stock(ServerLevel level, BlockPos absolutePos, int slot, Item item, int amount) {
        ResourceHandler<ItemResource> handler =
                level.getCapability(Capabilities.Item.BLOCK, absolutePos, null);
        if (handler == null) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(slot, ItemResource.of(item), amount, tx);
            tx.commit();
        }
    }

    private static void assertSlotHolds(GameTestHelper helper, ResourceHandler<ItemResource> handler,
                                        int slot, Item expected, String label) {
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty() || resource.getItem() != expected) {
            helper.fail("expected " + expected + " in the " + label + " slot (" + slot + "), found "
                    + (resource.isEmpty() ? "nothing" : resource.getItem()));
        }
    }

    /** The first stack of {@code item}, components and all, or null. */
    private static ItemStack findStack(ResourceHandler<ItemResource> handler, Item item) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (!resource.isEmpty() && resource.getItem() == item) {
                return resource.toStack(Math.max(1, handler.getAmountAsInt(slot)));
            }
        }
        return null;
    }

    private static int countIn(ResourceHandler<ItemResource> handler, Item item) {
        return AnchorTestFixture.countIn(handler, item);
    }
}
