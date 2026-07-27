package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ChronoAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import static com.skilles.chronoclones.gametest.AnchorTestFixture.countIn;

/**
 * Stocking the fake player before a container session runs.
 *
 * <p>The carrier layout is the one part of a session that is not a replayed gesture — the clicks are
 * the player's own, but the squares those clicks were made against have to be rebuilt first. So the
 * rule here is deliberately dull: the recorded item, up to the recorded amount, in the recorded
 * square. Anything cleverer is the mod guessing at intent, and the two guesses that were tried both
 * ended up putting things where the routine never touched.
 */
final class CarrierGameTest {

    private CarrierGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("carrier_every_square_gets_its_share",
                CarrierGameTest::everySquareGetsItsShare);
        ChronoclonesGameTests.add("carrier_takes_only_what_was_recorded",
                CarrierGameTest::takesOnlyWhatWasRecorded);
        ChronoclonesGameTests.add("carrier_clicks_the_square_it_recorded",
                CarrierGameTest::clicksTheSquareItRecorded);
        ChronoclonesGameTests.add("carrier_stack_survives_an_imprint",
                CarrierGameTest::stackSurvivesAnImprint);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    // Vanilla lays container slots out first, then the player's main inventory, then the hotbar.
    private static final int CHEST_MENU_SIZE = 27 + 36;
    private static final int CHEST_MAIN_INVENTORY_START = 27;

    private static final int LEFT = 0;

    /**
     * A session that stocks two squares stocks both of them.
     *
     * <p>Every other container test carries a single square, and with one square there is nothing to
     * starve — so this case had a clear run through a suite that looks like it covers it. It failed
     * for real: staging was once allowed to take as much of an item as the anchor held, which let
     * the first square empty the stock and left every square after it reporting the routine
     * unstocked with a full anchor sitting there.
     */
    private static void everySquareGetsItsShare(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int first = CHEST_MAIN_INVENTORY_START;
        int second = CHEST_MAIN_INVENTORY_START + 1;

        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(new ChronoAction.UseContainer.CarrierSlot(first, new ItemStack(Items.DIAMOND, 2)),
                        new ChronoAction.UseContainer.CarrierSlot(second, new ItemStack(Items.DIAMOND, 2))),
                List.of(click(first, ContainerInput.QUICK_MOVE),
                        click(second, ContainerInput.QUICK_MOVE)));
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.DIAMOND), 8);

        assertBarrelHolds(helper, target, Items.DIAMOND, 4,
                "both squares were stocked with the two diamonds they recorded");
    }

    /**
     * The recorded amount is the amount, however much the anchor is holding.
     *
     * <p>An anchor kept topped up is a supply, not an instruction to move the supply: a routine
     * taught by moving two of something moves two of it, and the rest stays home.
     */
    private static void takesOnlyWhatWasRecorded(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int square = CHEST_MAIN_INVENTORY_START;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(new ChronoAction.UseContainer.CarrierSlot(square, new ItemStack(Items.DIAMOND, 5))),
                List.of(click(square, ContainerInput.QUICK_MOVE)));
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.DIAMOND), 32);

        assertBarrelHolds(helper, target, Items.DIAMOND, 5, "only the recorded five moved");
        helper.startSequence()
                .thenExecuteAfter(16, () -> {
                    if (countIn(anchor.getInventory(), Items.DIAMOND) != 27) {
                        helper.fail("the remainder did not stay home: anchor holds "
                                + countIn(anchor.getInventory(), Items.DIAMOND) + " of 27");
                    }
                })
                .thenSucceed();
    }

    /**
     * A click lands on the square it recorded, whatever is in it.
     *
     * <p>The barrel's first square is full of something else, and the click goes there anyway — which
     * for a left click on an occupied square means a swap, exactly as it would for a player. An
     * earlier version went looking for another square of the same kind when the recorded one was
     * occupied; that is a guess at what the player meant, and it let an anchor put things somewhere
     * the routine had never touched.
     */
    private static void clicksTheSquareItRecorded(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);
        AnchorTestFixture.fillSlot(helper, target, 0, new ItemStack(Items.DIRT, 64));

        int square = CHEST_MAIN_INVENTORY_START;
        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(new ChronoAction.UseContainer.CarrierSlot(square, new ItemStack(Items.OAK_LOG, 1))),
                List.of(click(square, ContainerInput.PICKUP), click(0, ContainerInput.PICKUP)));
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.OAK_LOG), 1);

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
     *
     * <p>A carrier square records an {@code ItemStack} rather than an item and a count, which is what
     * lets the shard tooltip and the goggles name what a routine actually needs. The codec test that
     * would normally cover the round trip cannot build a populated stack without a datapack, so it
     * happens here instead.
     */
    private static void stackSurvivesAnImprint(GameTestHelper helper) {
        ItemStack recorded = new ItemStack(Items.DIAMOND, 5);
        recorded.set(DataComponents.CUSTOM_NAME, Component.literal("Keystone"));

        ChronoAnchorBlockEntity anchor = deposit(helper,
                List.of(new ChronoAction.UseContainer.CarrierSlot(CHEST_MAIN_INVENTORY_START, recorded)),
                List.of(click(CHEST_MAIN_INVENTORY_START, ContainerInput.QUICK_MOVE)));

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
                                                 List<ChronoAction.UseContainer.CarrierSlot> carrier,
                                                 List<ChronoAction.UseContainer.Click> clicks) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new BlockPos(0, 0, -1), CHEST_MENU_SIZE, carrier, clicks)));
        AnchorTestFixture.unlockAllActions(anchor);
        return anchor;
    }

    private static void assertBarrelHolds(GameTestHelper helper, BlockPos target,
                                          net.minecraft.world.item.Item item, int count,
                                          String what) {
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
                    if (countIn(barrel, item) != count) {
                        helper.fail("expected " + what + " - barrel holds "
                                + countIn(barrel, item) + " of " + count);
                    }
                })
                .thenSucceed();
    }

    private static ChronoAction.UseContainer.Click click(int slot, ContainerInput input) {
        return new ChronoAction.UseContainer.Click(slot, LEFT, input);
    }
}
