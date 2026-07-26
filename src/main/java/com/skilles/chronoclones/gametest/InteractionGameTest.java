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
 *
 * <p>These matter more than the count of them suggests. Every one goes through the server's own
 * entry point — {@code useItemOn} for blocks, the item-handler capability for containers — so what
 * they actually assert is that the mod contains <em>no</em> knowledge of levers or chests, and would
 * behave the same way for a block belonging to a mod that has not been written yet. A test that
 * special-cased its way to green here would be worse than none.
 */
final class InteractionGameTest {

    private InteractionGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("use_on_block_flips_a_lever", InteractionGameTest::flipsLever);
        ChronoclonesGameTests.add("use_needs_its_item_in_the_anchor", InteractionGameTest::needsItsItem);
        ChronoclonesGameTests.add("use_returns_what_it_borrowed", InteractionGameTest::returnsWhatItBorrowed);
        ChronoclonesGameTests.add("transfer_withdraws_from_a_container", InteractionGameTest::withdraws);
        ChronoclonesGameTests.add("transfer_deposits_into_a_container", InteractionGameTest::deposits);
        ChronoclonesGameTests.add("transfer_respects_which_slot", InteractionGameTest::respectsSlots);
        ChronoclonesGameTests.add("transfer_moves_within_one_container", InteractionGameTest::movesWithinContainer);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /**
     * Nothing in this mod knows what a lever is.
     *
     * <p>The routine says "right-click the top face of the block one step north". A lever happens to
     * be there, so it flips — for exactly the same reason it flips for a player.
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

        // Checked after one pass, not a full loop. A lever is a toggle and the routine repeats, so
        // waiting long enough for two passes asserts nothing at all.
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
     *
     * <p>Flint and steel lights a fire and loses a point of durability. The executor knows neither
     * fact: it hands over a real stack, lets the normal code path do whatever it does, and puts back
     * what is left in the hand. That one mechanism is what makes durability, consumption and
     * container-item swaps all come out right without a table of special cases.
     *
     * <p>Checked after a single pass rather than a full loop, so the assertion is an exact number
     * instead of "less than it started with".
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

    /** Pulling out of a container, through the capability rather than a simulated menu. */
    private static void withdraws(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        stock(level, helper.absolutePos(target), Items.COBBLESTONE, 32);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.TransferItems(
                        new BlockPos(0, 0, -1),
                        BuiltInRegistries.ITEM.wrapAsHolder(Items.COBBLESTONE),
                        32, 0, ChronoAction.TransferItems.CARRIER)));
        AnchorTestFixture.unlockAllActions(anchor);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    if (countIn(anchor.getInventory(), Items.COBBLESTONE) < 32) {
                        helper.fail("expected 32 cobblestone pulled out of the barrel, anchor holds "
                                + countIn(anchor.getInventory(), Items.COBBLESTONE));
                    }
                })
                .thenSucceed();
    }

    /** And putting back in. */
    private static void deposits(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.TransferItems(
                        new BlockPos(0, 0, -1),
                        BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND),
                        5, ChronoAction.TransferItems.CARRIER, 0)));
        AnchorTestFixture.unlockAllActions(anchor);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.DIAMOND), 5);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (countIn(barrel, Items.DIAMOND) != 5) {
                        helper.fail("expected 5 diamonds deposited, barrel holds "
                                + countIn(barrel, Items.DIAMOND));
                    }
                })
                .thenSucceed();
    }

    /**
     * The reason transfers carry slots at all: loading a furnace.
     *
     * <p>One log into the input and one into the fuel slot. Both slots accept a log, so a transfer
     * that only knew "the furnace gained two logs" would put both wherever they first fit and smelt
     * nothing — the failure would be silent, and the routine would look like it was working.
     */
    private static void respectsSlots(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.FURNACE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(
                        List.of(
                                transfer(Items.OAK_LOG, 1, ChronoAction.TransferItems.CARRIER, FURNACE_INPUT),
                                transfer(Items.OAK_LOG, 1, ChronoAction.TransferItems.CARRIER, FURNACE_FUEL))));
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

                    // The fuel slot is asserted through the furnace being lit rather than by reading
                    // it, because a furnace that receives fuel consumes it on the same tick — an
                    // empty fuel slot here means the log arrived and burned, which is the point.
                    if (!helper.getBlockState(target).getValue(BlockStateProperties.LIT)) {
                        helper.fail("the furnace never lit - the second log did not reach the fuel "
                                + "slot, so both went to the input and nothing smelts");
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
        ResourceHandler<ItemResource> barrel =
                level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
        if (barrel == null) {
            helper.fail("the barrel exposes no item handler");
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            barrel.insert(3, ItemResource.of(Items.COBBLESTONE), 16, tx);
            tx.commit();
        }

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(transfer(Items.COBBLESTONE, 16, 3, 7)));
        AnchorTestFixture.unlockAllActions(anchor);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
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

    /** Vanilla furnace slot meanings, which this mod knows nothing about — the test supplies them. */
    private static final int FURNACE_INPUT = 0;
    private static final int FURNACE_FUEL = 1;

    // ------------------------------------------------------------------ helpers

    private static ChronoAction transfer(Item item, int amount, int fromSlot, int toSlot) {
        return new ChronoAction.TransferItems(new BlockPos(0, 0, -1),
                BuiltInRegistries.ITEM.wrapAsHolder(item), amount, fromSlot, toSlot);
    }

    private static void assertSlotHolds(GameTestHelper helper, ResourceHandler<ItemResource> handler,
                                        int slot, Item expected, String label) {
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty() || resource.getItem() != expected) {
            helper.fail("expected " + expected + " in the " + label + " slot (" + slot + "), found "
                    + (resource.isEmpty() ? "nothing" : resource.getItem()));
        }
    }

    /** Right-click the top face, dead centre — the geometry a player clicking a floor block produces. */
    private static ChronoAction useOnBlock(BlockPos localPos, Item item) {
        return new ChronoAction.UseOnBlock(localPos, Direction.UP, new Vec3(0.0, 0.5, 0.0), false,
                InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(item));
    }

    private static void stock(ServerLevel level, BlockPos absolutePos, Item item, int amount) {
        ResourceHandler<ItemResource> handler =
                level.getCapability(Capabilities.Item.BLOCK, absolutePos, null);
        if (handler == null) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(ItemResource.of(item), amount, tx);
            tx.commit();
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
        int total = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (!resource.isEmpty() && resource.getItem() == item) {
                total += handler.getAmountAsInt(slot);
            }
        }
        return total;
    }
}
