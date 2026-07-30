package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import static com.skilles.chronoclones.gametest.AnchorTestFixture.countIn;

/**
 * Lending a clone's inventory to a container session, square for square.
 */
final class CarrierGameTest {

    private CarrierGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("carrier_lends_the_square_the_click_names",
                CarrierGameTest::lendsTheSquareTheClickNames);
        ChronoclonesGameTests.add("carrier_lends_the_hotbar_row_too",
                CarrierGameTest::lendsTheHotbarRowToo);
        ChronoclonesGameTests.add("carrier_returns_to_the_square_it_lent_from",
                CarrierGameTest::returnsToTheSquareItLentFrom);
        ChronoclonesGameTests.add("carrier_clicks_the_square_it_recorded",
                CarrierGameTest::clicksTheSquareItRecorded);
        ChronoclonesGameTests.add("carrier_stack_survives_an_imprint",
                CarrierGameTest::stackSurvivesAnImprint);
        ChronoclonesGameTests.add("carrier_quantity_rule_caps_what_it_lends",
                CarrierGameTest::quantityRuleCapsWhatItLends);
        ChronoclonesGameTests.add("carrier_item_rule_holds_back_what_it_names",
                CarrierGameTest::itemRuleHoldsBackWhatItNames);
        ChronoclonesGameTests.add("carrier_exact_slot_rule_lends_one_square",
                CarrierGameTest::exactSlotRuleLendsOneSquare);
    }

    /** A ceiling on the amount, for a routine that should feed a furnace rather than fill it. */
    private static void quantityRuleCapsWhatItLends(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int lent = 9;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(lent), LEFT, ContainerInput.QUICK_MOVE)),
                ActionSettings.DEFAULT.withTransfer(ActionSettings.TransferRule.DEFAULT
                        .withQuantity(ActionSettings.QuantityRule.atMost(5))));
        anchor.getCloneInventory(0).set(lent, ItemResource.of(Items.DIAMOND), 32);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 5);
                    if (countIn(anchor.getCloneInventory(0), Items.DIAMOND) != 27) {
                        helper.fail("the 27 it was not allowed to carry did not stay home: "
                                + countIn(anchor.getCloneInventory(0), Items.DIAMOND));
                    }
                })
                .thenSucceed();
    }

    /** An item rule leaves everything it does not name behind, so the clicks find an empty square. */
    private static void itemRuleHoldsBackWhatItNames(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int lent = 9;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(lent), LEFT, ContainerInput.QUICK_MOVE)),
                ActionSettings.DEFAULT.withTransfer(ActionSettings.TransferRule.DEFAULT
                        .withItems(List.of(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .wrapAsHolder(Items.OAK_LOG)))));
        anchor.getCloneInventory(0).set(lent, ItemResource.of(Items.DIAMOND), 32);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 0);
                    if (countIn(anchor.getCloneInventory(0), Items.DIAMOND) != 32) {
                        helper.fail("diamonds moved despite a rule that names only logs");
                    }
                })
                .thenSucceed();
    }

    /** An exact slot rule confines a session to one square of the clone's inventory. */
    private static void exactSlotRuleLendsOneSquare(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int allowed = 9;
        int withheld = 10;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(allowed), LEFT, ContainerInput.QUICK_MOVE),
                        click(menuSlotOf(withheld), LEFT, ContainerInput.QUICK_MOVE)),
                ActionSettings.DEFAULT.withSlot(
                        new ActionSettings.SlotRule(ActionSettings.SlotRule.Mode.EXACT, allowed)));
        anchor.getCloneInventory(0).set(allowed, ItemResource.of(Items.DIAMOND), 4);
        anchor.getCloneInventory(0).set(withheld, ItemResource.of(Items.OAK_LOG), 4);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 4);
                    assertBarrelHolds(helper, target, Items.OAK_LOG, 0);
                    if (countIn(anchor.getCloneInventory(0), Items.OAK_LOG) != 4) {
                        helper.fail("a square the rule excludes was lent anyway");
                    }
                })
                .thenSucceed();
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** A single chest: 27 of its own, then the player's storage rows, then the hotbar. */
    private static final int CHEST_SLOTS = 27;
    private static final int CHEST_MENU_SIZE = CHEST_SLOTS + Inventory.INVENTORY_SIZE;

    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    /** Where a clone's inventory slot appears in the open chest menu. */
    private static int menuSlotOf(int inventorySlot) {
        return Inventory.isHotbarSlot(inventorySlot)
                ? CHEST_SLOTS + (Inventory.INVENTORY_SIZE - Inventory.SELECTION_SIZE) + inventorySlot
                : CHEST_SLOTS + inventorySlot - Inventory.SELECTION_SIZE;
    }

    /**
     * The click names a square, and that square holds what the anchor holds in it.
     */
    private static void lendsTheSquareTheClickNames(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int lent = 9;
        int untouched = 20;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(lent), LEFT, ContainerInput.QUICK_MOVE)));
        anchor.getCloneInventory(0).set(lent, ItemResource.of(Items.DIAMOND), 32);
        anchor.getCloneInventory(0).set(untouched, ItemResource.of(Items.OAK_LOG), 5);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 32);
                    if (!anchor.getCloneInventory(0).getResource(lent).isEmpty()) {
                        helper.fail("the lent square was refilled behind the session's back");
                    }
                    // A session borrows the whole inventory, so everything else must survive it.
                    if (countIn(anchor.getCloneInventory(0), Items.OAK_LOG) != 5) {
                        helper.fail("the logs the session never touched did not come home");
                    }
                })
                .thenSucceed();
    }

    /** The hotbar sits at the far end of the menu, 27 squares from where the storage rows start. */
    private static void lendsTheHotbarRowToo(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int hotbar = 3;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(hotbar), LEFT, ContainerInput.QUICK_MOVE)));
        anchor.getCloneInventory(0).set(hotbar, ItemResource.of(Items.DIAMOND), 7);

        helper.startSequence()
                .thenExecuteAfter(15, () -> assertBarrelHolds(helper, target, Items.DIAMOND, 7))
                .thenSucceed();
    }

    /** What the session did not spend goes back where it came from, not wherever there is room. */
    private static void returnsToTheSquareItLentFrom(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int lent = 14;
        ChronoAnchorBlockEntity anchor = deposit(helper, List.of(
                // Right-click takes half to the cursor, left-click puts it in the barrel.
                click(menuSlotOf(lent), RIGHT, ContainerInput.PICKUP),
                click(0, LEFT, ContainerInput.PICKUP)));
        anchor.getCloneInventory(0).set(lent, ItemResource.of(Items.DIAMOND), 32);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 16);
                    int home = anchor.getCloneInventory(0).getAmountAsInt(lent);
                    if (home != 16) {
                        helper.fail("expected the other 16 back in square " + lent + ", found " + home);
                    }
                })
                .thenSucceed();
    }

    /**
     * A click lands on the square it recorded, whatever is in it.
     */
    private static void clicksTheSquareItRecorded(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);
        AnchorTestFixture.fillSlot(helper, target, 0, new ItemStack(Items.DIRT, 64));

        int lent = 9;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(lent), LEFT, ContainerInput.PICKUP),
                        click(0, LEFT, ContainerInput.PICKUP)));
        anchor.getCloneInventory(0).set(lent, ItemResource.of(Items.OAK_LOG), 1);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(target);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absolute, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (barrel.getResource(0).getItem() != Items.OAK_LOG) {
                        helper.fail("the click did not land on the square it named: square 0 holds "
                                + barrel.getResource(0).getItem());
                    }
                    if (!barrel.getResource(1).isEmpty()) {
                        helper.fail("it moved along to the next square: square 1 holds "
                                + barrel.getResource(1).getItem());
                    }
                    if (countIn(anchor.getInventory(), Items.DIRT) != 64) {
                        helper.fail("the displaced dirt did not come home: anchor holds "
                                + countIn(anchor.getInventory(), Items.DIRT) + " of 64");
                    }
                })
                .thenSucceed();
    }

    /**
     * A carrier square's whole stack survives being imprinted.
     */
    private static void stackSurvivesAnImprint(GameTestHelper helper) {
        ItemStack recorded = new ItemStack(Items.DIAMOND, 5);
        recorded.set(DataComponents.CUSTOM_NAME, Component.literal("Keystone"));

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), CHEST_MENU_SIZE,
                        List.of(new ChronoAction.UseContainer.CarrierSlot(menuSlotOf(9), recorded)),
                        List.of(click(menuSlotOf(9), LEFT, ContainerInput.QUICK_MOVE)))));

        if (anchor.getRecording() == null) {
            helper.fail("the anchor kept no recording");
            return;
        }
        ChronoAction action = anchor.getRecording().actions().getFirst().action();
        if (!(action instanceof ChronoAction.UseContainer session)) {
            helper.fail("expected a container session, got " + action.type());
            return;
        }
        ItemStack kept = session.carrier().getFirst().stack();
        if (!ItemStack.matches(recorded, kept)) {
            helper.fail("the carrier stack changed across the imprint: recorded "
                    + recorded.getHoverName().getString() + " x" + recorded.getCount()
                    + ", kept " + kept.getHoverName().getString() + " x" + kept.getCount());
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------- helpers

    private static ChronoAnchorBlockEntity deposit(GameTestHelper helper,
                                                   List<SessionStep> clicks) {
        return deposit(helper, clicks, ActionSettings.DEFAULT);
    }

    private static ChronoAnchorBlockEntity deposit(GameTestHelper helper,
                                                   List<SessionStep> clicks,
                                                   ActionSettings settings) {
        return AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), CHEST_MENU_SIZE, List.of(), clicks), settings));
    }

    private static void assertBarrelHolds(GameTestHelper helper, BlockPos target,
                                          net.minecraft.world.item.Item item, int count) {
        ResourceHandler<ItemResource> barrel = helper.getLevel().getCapability(
                Capabilities.Item.BLOCK, helper.absolutePos(target), null);
        if (barrel == null) {
            helper.fail("the barrel exposes no item handler");
            return;
        }
        if (countIn(barrel, item) != count) {
            helper.fail("expected " + count + " " + item + " in the barrel, found "
                    + countIn(barrel, item));
        }
    }

    private static SessionStep.RawClick click(int slot, int button, ContainerInput input) {
        return new SessionStep.RawClick(slot, button, input);
    }
}
