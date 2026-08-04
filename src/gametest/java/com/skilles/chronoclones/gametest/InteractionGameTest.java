package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;

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
import net.minecraft.world.level.block.Block;
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

final class InteractionGameTest {

    private InteractionGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("use_on_block_flips_a_lever", InteractionGameTest::flipsLever);
        ChronoclonesGameTests.add("use_on_block_refuses_a_block_it_was_not_recorded_on",
                InteractionGameTest::useOnRefusesAnotherBlock);
        ChronoclonesGameTests.add("use_on_block_widened_works_whatever_is_there",
                InteractionGameTest::widenedUseOnWorksAnyway);
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
        ChronoclonesGameTests.add("container_untouched_stock_comes_home",
                InteractionGameTest::untouchedStockComesHome);
        ChronoclonesGameTests.add("container_runs_understocked", InteractionGameTest::runsUnderstocked);
        ChronoclonesGameTests.add("move_step_takes_half_of_what_is_there",
                InteractionGameTest::moveStepTakesHalf);
        ChronoclonesGameTests.add("move_step_drops_a_single_item",
                InteractionGameTest::moveStepDropsOne);
        ChronoclonesGameTests.add("move_step_sends_a_stack_elsewhere",
                InteractionGameTest::moveStepSendsElsewhere);
        ChronoclonesGameTests.add("move_step_over_an_empty_square_does_nothing",
                InteractionGameTest::moveStepOverNothing);
        ChronoclonesGameTests.add("a_right_drag_takes_one_item_out_of_a_stack",
                InteractionGameTest::rightDragTakesOneItem);
        ChronoclonesGameTests.add("a_session_on_a_plain_block_says_there_is_nothing_to_open",
                InteractionGameTest::sessionWithoutAMenuSaysSo);
    }

    private static void sessionWithoutAMenuSaysSo(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        click(0, LEFT, ContainerInput.QUICK_MOVE))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (anchor.getLastFailure().reason()
                            != DiagnosticState.FailureReason.NO_MENU) {
                        helper.fail("expected a block with no menu to say so, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void rightDragTakesOneItem(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);
        stock(level, absoluteTarget, 0, Items.DIAMOND, 8);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        click(0, RIGHT, ContainerInput.PICKUP),
                        click(-999, 4, ContainerInput.QUICK_CRAFT),
                        click(CHEST_MAIN_INVENTORY_START + 2, 5, ContainerInput.QUICK_CRAFT),
                        click(-999, 6, ContainerInput.QUICK_CRAFT),
                        click(0, LEFT, ContainerInput.PICKUP))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    int taken = countIn(anchor.getInventory(), Items.DIAMOND);
                    if (taken != 1) {
                        helper.fail("expected one diamond dragged out, the anchor holds " + taken
                                + ", reporting " + anchor.getLastFailure().reason());
                        return;
                    }
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
                    if (barrel != null && barrel.getAmountAsInt(0) != 7) {
                        helper.fail("the rest of the stack was not put back, slot 0 holds "
                                + barrel.getAmountAsInt(0));
                    }
                })
                .thenSucceed();
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static void flipsLever(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target.below(), Blocks.STONE);
        helper.setBlock(target, Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.NORTH)
                .setValue(LeverBlock.POWERED, false));

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(useOnBlock(new BlockPos(0, 0, -1), Items.AIR)));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (!helper.getBlockState(target).getValue(LeverBlock.POWERED)) {
                        helper.fail("the routine right-clicked a lever and it did not flip");
                    }
                })
                .thenSucceed();
    }

    private static void needsItsItem(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(useOnBlock(new BlockPos(0, 0, -1), Items.BONE_MEAL)));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("expected NO_ITEM for a routine whose item is not stocked, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void returnsWhatItBorrowed(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(useOnBlock(new BlockPos(0, 0, -1), Items.FLINT_AND_STEEL)));
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.FLINT_AND_STEEL, 1));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertBlockPresent(Blocks.FIRE, target.above());

                    ItemStack returned = AnchorTestFixture.findStack(anchor.getInventory(), Items.FLINT_AND_STEEL);
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

    private static void splitsByIntent(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);
        stock(level, absoluteTarget, 0, Items.COBBLESTONE, 40);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        click(0, RIGHT, ContainerInput.PICKUP),
                        click(CHEST_CARRIER_SLOT, LEFT, ContainerInput.PICKUP))));

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

    private static void loadsAFurnace(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.FURNACE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(FURNACE_MENU_SIZE,
                        List.of(carrying(FURNACE_CARRIER_SLOT, Items.OAK_LOG, 2)),
                        click(FURNACE_CARRIER_SLOT, LEFT, ContainerInput.PICKUP),
                        click(FURNACE_INPUT, RIGHT, ContainerInput.PICKUP),
                        click(FURNACE_FUEL, RIGHT, ContainerInput.PICKUP))));
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.OAK_LOG, 2));

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

                    if (!helper.getBlockState(target).getValue(BlockStateProperties.LIT)) {
                        helper.fail("the furnace never lit - the second log did not reach the fuel "
                                + "slot, so nothing smelts");
                    }
                })
                .thenSucceed();
    }

    private static void shiftClicksOut(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        stock(level, helper.absolutePos(target), 0, Items.DIAMOND, 5);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        click(0, LEFT, ContainerInput.QUICK_MOVE))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (countIn(anchor.getInventory(), Items.DIAMOND) != 5) {
                        helper.fail("expected 5 diamonds shift-clicked into the anchor, got "
                                + countIn(anchor.getInventory(), Items.DIAMOND));
                    }
                })
                .thenSucceed();
    }

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

    private static void refusesAnotherMenu(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.FURNACE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        click(0, LEFT, ContainerInput.QUICK_MOVE))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("expected WRONG_BLOCK for a chest routine run against a furnace, "
                                + "got " + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void depositsIntoAContainer(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int inventorySlot = 13;
        int menuSlot = CHEST_MAIN_INVENTORY_START + inventorySlot - 9;

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        List.of(carrying(menuSlot, Items.DIAMOND, 5)),
                        click(menuSlot, LEFT, ContainerInput.QUICK_MOVE))));
        anchor.getCloneInventory(0).setItem(inventorySlot, new ItemStack(Items.DIAMOND, 5));

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

    private static void runsUnderstocked(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        List.of(carrying(CHEST_MAIN_INVENTORY_START, Items.DIAMOND, 5)),
                        click(CHEST_MAIN_INVENTORY_START, LEFT, ContainerInput.QUICK_MOVE))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NONE) {
                        helper.fail("an empty clone should replay its clicks over empty squares, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void moveStepTakesHalf(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);
        stock(level, absoluteTarget, 3, Items.COBBLESTONE, 24);

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        move(3, 7, Items.COBBLESTONE, SessionStep.Amount.HALF))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (barrel.getAmountAsInt(7) != 12) {
                        helper.fail("expected half of 24 moved, slot 7 holds "
                                + barrel.getAmountAsInt(7));
                    }
                    if (barrel.getAmountAsInt(3) != 12) {
                        helper.fail("the other half did not stay put, slot 3 holds "
                                + barrel.getAmountAsInt(3));
                    }
                })
                .thenSucceed();
    }

    private static void moveStepDropsOne(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);
        stock(level, absoluteTarget, 3, Items.COBBLESTONE, 24);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        move(3, 7, Items.COBBLESTONE, SessionStep.Amount.ONE))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absoluteTarget, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (barrel.getAmountAsInt(7) != 1) {
                        helper.fail("expected one item moved, slot 7 holds "
                                + barrel.getAmountAsInt(7));
                    }
                    if (barrel.getAmountAsInt(3) != 23) {
                        helper.fail("the remainder was not put back, slot 3 holds "
                                + barrel.getAmountAsInt(3));
                    }
                    if (countIn(anchor.getInventory(), Items.COBBLESTONE) != 0) {
                        helper.fail("the remainder came home with the clone instead of staying in "
                                + "the barrel");
                    }
                })
                .thenSucceed();
    }

    private static void moveStepSendsElsewhere(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        stock(level, helper.absolutePos(target), 0, Items.DIAMOND, 5);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        move(0, SessionStep.Move.ELSEWHERE, Items.DIAMOND,
                                SessionStep.Amount.ALL))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (countIn(anchor.getInventory(), Items.DIAMOND) != 5) {
                        helper.fail("expected 5 diamonds sent into the anchor, got "
                                + countIn(anchor.getInventory(), Items.DIAMOND));
                    }
                })
                .thenSucceed();
    }

    private static void moveStepOverNothing(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absoluteTarget = helper.absolutePos(target);
        stock(level, absoluteTarget, 5, Items.DIAMOND, 3);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        move(3, 7, Items.COBBLESTONE, SessionStep.Amount.ALL),
                        move(5, SessionStep.Move.ELSEWHERE, Items.DIAMOND,
                                SessionStep.Amount.ALL))));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (countIn(anchor.getInventory(), Items.DIAMOND) != 3) {
                        helper.fail("the step after the empty one never ran: the anchor holds "
                                + countIn(anchor.getInventory(), Items.DIAMOND) + " diamonds");
                    }
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NONE) {
                        helper.fail("an empty source square was reported as "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static final int CHEST_MENU_SIZE = 27 + 36;
    private static final int CHEST_CARRIER_SLOT = 27 + 27;
    private static final int CHEST_MAIN_INVENTORY_START = 27;
    private static final int FURNACE_MENU_SIZE = 3 + 36;
    private static final int FURNACE_CARRIER_SLOT = 3 + 27;
    private static final int FURNACE_INPUT = 0;
    private static final int FURNACE_FUEL = 1;

    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    private static void fullSlotLeavesTheItemAlone(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.FURNACE);
        AnchorTestFixture.fillSlot(helper, target, FURNACE_FUEL, new ItemStack(Items.COAL, 64));

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(FURNACE_MENU_SIZE,
                        List.of(carrying(FURNACE_CARRIER_SLOT, Items.COAL, 1)),
                        click(FURNACE_CARRIER_SLOT, LEFT, ContainerInput.PICKUP),
                        click(FURNACE_FUEL, LEFT, ContainerInput.PICKUP))));
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.COAL, 1));

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
                    if (countIn(anchor.getInventory(), Items.COAL) != 1) {
                        helper.fail("the coal went nowhere and was not returned to the anchor");
                    }
                })
                .thenSucceed();
    }

    private static void untouchedStockComesHome(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(session(CHEST_MENU_SIZE,
                        List.of(carrying(CHEST_MAIN_INVENTORY_START, Items.DIAMOND, 5)),
                        click(CHEST_MAIN_INVENTORY_START, LEFT, ContainerInput.QUICK_MOVE))));

        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.GOLD_INGOT, 12));
        anchor.getCloneInventory(0).setItem(1, new ItemStack(Items.IRON_INGOT, 7));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    int gold = countIn(anchor.getInventory(), Items.GOLD_INGOT);
                    int iron = countIn(anchor.getInventory(), Items.IRON_INGOT);
                    if (gold != 12 || iron != 7) {
                        helper.fail("a session ate stock its clicks never named: "
                                + gold + " gold and " + iron + " iron left of 12 and 7");
                    }
                })
                .thenSucceed();
    }

    private static SessionStep.RawClick click(int slot, int button, ContainerInput input) {
        return new SessionStep.RawClick(slot, button, input);
    }

    private static ChronoAction session(int menuSize, SessionStep... steps) {
        return session(menuSize, List.of(), steps);
    }

    private static ChronoAction session(int menuSize, List<ChronoAction.UseContainer.CarrierSlot> carrier,
                                      SessionStep... steps) {
        return new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), menuSize, carrier, List.of(steps));
    }

    private static SessionStep.Move move(int from, int to, Item item, SessionStep.Amount amount) {
        return new SessionStep.Move(from, to, BuiltInRegistries.ITEM.wrapAsHolder(item), amount);
    }

    private static ChronoAction.UseContainer.CarrierSlot carrying(int menuSlot, Item item, int count) {
        return new ChronoAction.UseContainer.CarrierSlot(menuSlot, new ItemStack(item, count));
    }

    private static ChronoAction useOnBlock(BlockPos localPos, Item item) {
        return new ChronoAction.UseOnBlock(localPos, Direction.UP, new Vec3(0.0, 0.5, 0.0), false,
                InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(item));
    }

    private static ChronoAction useOnBlock(BlockPos localPos, Item item, Block on) {
        return new ChronoAction.UseOnBlock(localPos, Direction.UP, new Vec3(0.0, 0.5, 0.0), false,
                InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(item),
                java.util.Optional.of(BuiltInRegistries.BLOCK.wrapAsHolder(on)));
    }

    private static void useOnRefusesAnotherBlock(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);
        helper.setBlock(target.above(), Blocks.AIR);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(
                        useOnBlock(new BlockPos(0, 0, -1), Items.DIAMOND_HOE, Blocks.DIRT), 0));
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND_HOE, 1));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertBlockPresent(Blocks.STONE, target);
                    if (anchor.getLastFailure().reason()
                            != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("expected WRONG_BLOCK for a hoe aimed at stone, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void widenedUseOnWorksAnyway(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.COARSE_DIRT);
        helper.setBlock(target.above(), Blocks.AIR);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(
                        useOnBlock(new BlockPos(0, 0, -1), Items.DIAMOND_HOE, Blocks.DIRT),
                        ActionSettings.DEFAULT
                                .withSlot(ActionSettings.SlotRule.prefer(0))
                                .withRecordedSubject(false)));
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND_HOE, 1));

        helper.startSequence()
                .thenExecuteAfter(20, () -> helper.assertBlockPresent(Blocks.DIRT, target))
                .thenSucceed();
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

    private static int countIn(net.minecraft.world.Container container, Item item) {
        return AnchorTestFixture.countIn(container, item);
    }
}
