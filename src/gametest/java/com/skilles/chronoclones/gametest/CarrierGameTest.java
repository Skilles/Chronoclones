package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;

import net.minecraft.core.BlockPos;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static com.skilles.chronoclones.gametest.AnchorTestFixture.countIn;

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
        ChronoclonesGameTests.add("carrier_lends_every_square_it_has",
                CarrierGameTest::exactSlotRuleLendsOneSquare);
    }

    private static void quantityRuleCapsWhatItLends(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int lent = 9;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(lent), LEFT, ContainerInput.QUICK_MOVE)),
                ActionSettings.DEFAULT.withTransfer(ActionSettings.TransferRule.DEFAULT
                        .withQuantity(ActionSettings.QuantityRule.atMost(5))));
        anchor.getCloneInventory(0).setItem(lent, new ItemStack(Items.DIAMOND, 32));

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

    private static void itemRuleHoldsBackWhatItNames(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int lent = 9;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(lent), LEFT, ContainerInput.QUICK_MOVE)),
                ActionSettings.DEFAULT.withTransfer(ActionSettings.TransferRule.DEFAULT
                        .withItems(List.of(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .wrapAsHolder(Items.OAK_LOG)))));
        anchor.getCloneInventory(0).setItem(lent, new ItemStack(Items.DIAMOND, 32));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 0);
                    if (countIn(anchor.getCloneInventory(0), Items.DIAMOND) != 32) {
                        helper.fail("diamonds moved despite a rule that names only logs");
                    }
                })
                .thenSucceed();
    }

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
        anchor.getCloneInventory(0).setItem(allowed, new ItemStack(Items.DIAMOND, 4));
        anchor.getCloneInventory(0).setItem(withheld, new ItemStack(Items.OAK_LOG, 4));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 4);
                    assertBarrelHolds(helper, target, Items.OAK_LOG, 4);
                    if (countIn(anchor.getCloneInventory(0), Items.OAK_LOG) != 0) {
                        helper.fail("a square the session never named was held back from it");
                    }
                })
                .thenSucceed();
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static final int CHEST_SLOTS = 27;
    private static final int CHEST_MENU_SIZE = CHEST_SLOTS + Inventory.INVENTORY_SIZE;

    private static final int LEFT = 0;
    private static final int RIGHT = 1;

    private static int menuSlotOf(int inventorySlot) {
        //? if >=26 {
        return Inventory.isHotbarSlot(inventorySlot)
                ? CHEST_SLOTS + (Inventory.INVENTORY_SIZE - Inventory.SELECTION_SIZE) + inventorySlot
                : CHEST_SLOTS + inventorySlot - Inventory.SELECTION_SIZE;
        //?} else {
        /*return Inventory.isHotbarSlot(inventorySlot)
                ? CHEST_SLOTS + (Inventory.INVENTORY_SIZE - Inventory.getSelectionSize()) + inventorySlot
                : CHEST_SLOTS + inventorySlot - Inventory.getSelectionSize();
        *///?}
    }

    private static void lendsTheSquareTheClickNames(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int lent = 9;
        int untouched = 20;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(lent), LEFT, ContainerInput.QUICK_MOVE)));
        anchor.getCloneInventory(0).setItem(lent, new ItemStack(Items.DIAMOND, 32));
        anchor.getCloneInventory(0).setItem(untouched, new ItemStack(Items.OAK_LOG, 5));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 32);
                    if (!anchor.getCloneInventory(0).getItem(lent).isEmpty()) {
                        helper.fail("the lent square was refilled behind the session's back");
                    }
                    if (countIn(anchor.getCloneInventory(0), Items.OAK_LOG) != 5) {
                        helper.fail("the logs the session never touched did not come home");
                    }
                })
                .thenSucceed();
    }

    private static void lendsTheHotbarRowToo(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int hotbar = 3;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(hotbar), LEFT, ContainerInput.QUICK_MOVE)));
        anchor.getCloneInventory(0).setItem(hotbar, new ItemStack(Items.DIAMOND, 7));

        helper.startSequence()
                .thenExecuteAfter(15, () -> assertBarrelHolds(helper, target, Items.DIAMOND, 7))
                .thenSucceed();
    }

    private static void returnsToTheSquareItLentFrom(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int lent = 14;
        ChronoAnchorBlockEntity anchor = deposit(helper, List.of(
                click(menuSlotOf(lent), RIGHT, ContainerInput.PICKUP),
                click(0, LEFT, ContainerInput.PICKUP)));
        anchor.getCloneInventory(0).setItem(lent, new ItemStack(Items.DIAMOND, 32));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    assertBarrelHolds(helper, target, Items.DIAMOND, 16);
                    int home = anchor.getCloneInventory(0).getItem(lent).getCount();
                    if (home != 16) {
                        helper.fail("expected the other 16 back in square " + lent + ", found " + home);
                    }
                })
                .thenSucceed();
    }

    private static void clicksTheSquareItRecorded(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);
        AnchorTestFixture.fillSlot(helper, target, 0, new ItemStack(Items.DIRT, 64));

        int lent = 9;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(click(menuSlotOf(lent), LEFT, ContainerInput.PICKUP),
                        click(0, LEFT, ContainerInput.PICKUP)));
        anchor.getCloneInventory(0).setItem(lent, new ItemStack(Items.OAK_LOG, 1));

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(target);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (!TestItemPipes.present(level, absolute)) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (TestItemPipes.slot(level, absolute, 0).getItem() != Items.OAK_LOG) {
                        helper.fail("the click did not land on the square it named: square 0 holds "
                                + TestItemPipes.slot(level, absolute, 0).getItem());
                    }
                    if (!TestItemPipes.slot(level, absolute, 1).isEmpty()) {
                        helper.fail("it moved along to the next square: square 1 holds "
                                + TestItemPipes.slot(level, absolute, 1).getItem());
                    }
                    if (countIn(anchor.getInventory(), Items.DIRT) != 64) {
                        helper.fail("the displaced dirt did not come home: anchor holds "
                                + countIn(anchor.getInventory(), Items.DIRT) + " of 64");
                    }
                })
                .thenSucceed();
    }

    private static void stackSurvivesAnImprint(GameTestHelper helper) {
        ItemStack recorded = new ItemStack(Items.DIAMOND, 5);
        //? if >=1.20.5 {
        recorded.set(DataComponents.CUSTOM_NAME, Component.literal("Keystone"));
        //?} else {
        /*recorded.setHoverName(Component.literal("Keystone"));
        *///?}

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
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(target);
        if (!TestItemPipes.present(level, absolute)) {
            helper.fail("the barrel exposes no item handler");
            return;
        }
        if (TestItemPipes.count(level, absolute, item) != count) {
            helper.fail("expected " + count + " " + item + " in the barrel, found "
                    + TestItemPipes.count(level, absolute, item));
        }
    }

    private static SessionStep.RawClick click(int slot, int button, ContainerInput input) {
        return new SessionStep.RawClick(slot, button, input);
    }
}
