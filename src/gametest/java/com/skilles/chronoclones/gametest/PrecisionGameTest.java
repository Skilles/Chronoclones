package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.network.AnchorPrecisionPayload;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.replay.TransferPrecision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import static com.skilles.chronoclones.gametest.AnchorTestFixture.countIn;

/**
 * The three axes an anchor can be specific about, one test per direction that matters.
 *
 * <p>These need a running server for a reason worth stating: the whole point of the item axis is
 * telling one diamond pickaxe from another, which lives in an {@code ItemStack}'s data components —
 * and components are bound during datapack load, so a plain JUnit test cannot construct a populated
 * stack at all, let alone two that differ. Everything here is therefore behavioural rather than
 * structural; the bit packing is asserted in {@code TransferPrecisionTest}.
 *
 * <p>Each pair runs the same routine against the same stock twice, differing only in the flag. A
 * test that only checked the specific direction would pass just as well against an anchor that had
 * quietly stopped doing anything.
 */
final class PrecisionGameTest {

    private PrecisionGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("precision_lenient_item_substitutes",
                PrecisionGameTest::lenientItemSubstitutes);
        ChronoclonesGameTests.add("precision_exact_item_refuses_a_substitute",
                PrecisionGameTest::exactItemRefusesASubstitute);
        ChronoclonesGameTests.add("precision_exact_item_reads_components",
                PrecisionGameTest::exactItemReadsComponents);
        ChronoclonesGameTests.add("precision_lenient_item_prefers_what_was_recorded",
                PrecisionGameTest::lenientItemPrefersWhatWasRecorded);
        ChronoclonesGameTests.add("precision_lenient_quantity_takes_everything",
                PrecisionGameTest::lenientQuantityTakesEverything);
        ChronoclonesGameTests.add("precision_exact_quantity_caps_at_the_recorded_count",
                PrecisionGameTest::exactQuantityCapsAtTheRecordedCount);
        ChronoclonesGameTests.add("precision_lenient_slot_moves_along",
                PrecisionGameTest::lenientSlotMovesAlong);
        ChronoclonesGameTests.add("precision_exact_slot_refuses_a_substitute",
                PrecisionGameTest::exactSlotRefusesASubstitute);
        ChronoclonesGameTests.add("precision_carrier_stack_survives_an_imprint",
                PrecisionGameTest::carrierStackSurvivesAnImprint);
        ChronoclonesGameTests.add("precision_every_carried_square_gets_its_share",
                PrecisionGameTest::everyCarriedSquareGetsItsShare);
        ChronoclonesGameTests.add("precision_the_screen_can_set_it",
                PrecisionGameTest::theScreenCanSetIt);
        ChronoclonesGameTests.add("precision_a_stranger_cannot_set_it",
                PrecisionGameTest::aStrangerCannotSetIt);
        ChronoclonesGameTests.add("precision_survives_a_save_and_load",
                PrecisionGameTest::settingSurvivesASaveAndLoad);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    // Vanilla lays container slots out first, then the player's main inventory, then the hotbar.
    private static final int CHEST_MENU_SIZE = 27 + 36;
    private static final int CHEST_MAIN_INVENTORY_START = 27;

    private static final int LEFT = 0;

    // ------------------------------------------------------------------------ item

    /**
     * Item off: whatever the anchor has will do.
     *
     * <p>The routine deposits diamonds; the anchor is stocked with gold. It deposits the gold, and
     * that is the intended reading of "the item was incidental" — a routine taught by moving one
     * thing into a chest keeps working when you restock the chest run with another.
     */
    private static void lenientItemSubstitutes(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = deposit(helper, Items.DIAMOND, 5);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.GOLD_INGOT), 5);

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
                    if (countIn(barrel, Items.GOLD_INGOT) != 5) {
                        helper.fail("a routine that is not specific about items refused a substitute: "
                                + "the barrel holds " + countIn(barrel, Items.GOLD_INGOT) + " gold");
                    }
                })
                .thenSucceed();
    }

    /** Item on: only the recorded thing, and saying so rather than moving the wrong one. */
    private static void exactItemRefusesASubstitute(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = deposit(helper, Items.DIAMOND, 5);
        anchor.setPrecision(new TransferPrecision(false, true, false));
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.GOLD_INGOT), 5);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(target);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("expected NO_ITEM from an anchor specific about items, got "
                                + anchor.getLastFailure().reason());
                    }
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absolute, null);
                    if (barrel != null && countIn(barrel, Items.GOLD_INGOT) != 0) {
                        helper.fail("it refused the substitute and deposited it anyway");
                    }
                })
                .thenSucceed();
    }

    /**
     * The reason a carrier slot records a whole stack rather than an item id.
     *
     * <p>Same item, different components: a renamed diamond is not the diamond this routine was
     * taught with. Nothing below the stack level can tell those apart, so if this passes with the
     * name ignored, the item axis is only half of what it claims to be.
     */
    private static void exactItemReadsComponents(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ItemStack recorded = new ItemStack(Items.DIAMOND, 5);
        recorded.set(DataComponents.CUSTOM_NAME, Component.literal("Keystone"));

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new BlockPos(0, 0, -1), CHEST_MENU_SIZE,
                        List.of(new ChronoAction.UseContainer.CarrierSlot(
                                CHEST_MAIN_INVENTORY_START, recorded)),
                        List.of(click(CHEST_MAIN_INVENTORY_START, ContainerInput.QUICK_MOVE)))));
        AnchorTestFixture.unlockAllActions(anchor);
        anchor.setPrecision(new TransferPrecision(false, true, false));

        // Plain diamonds. Same item, no name — which is the whole difference.
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.DIAMOND), 5);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("a named stack was satisfied by an unnamed one: components are "
                                + "not being compared, got " + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /**
     * Leniency is a fallback, not a coin toss.
     *
     * <p>The anchor holds gold in its first slot and the recorded diamonds in its second. An anchor
     * that took "anything will do" as "take whatever comes first" would deposit the gold and leave a
     * correctly stocked routine doing the wrong thing for a reason no player could see.
     */
    private static void lenientItemPrefersWhatWasRecorded(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = deposit(helper, Items.DIAMOND, 5);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.GOLD_INGOT), 5);
        anchor.getInventoryHandler().set(1, ItemResource.of(Items.DIAMOND), 5);

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
                    if (countIn(barrel, Items.DIAMOND) != 5) {
                        helper.fail("the recorded item was available and something else went instead: "
                                + "barrel holds " + countIn(barrel, Items.DIAMOND) + " diamonds and "
                                + countIn(barrel, Items.GOLD_INGOT) + " gold");
                    }
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------- quantity

    /**
     * Quantity off: the recorded count was incidental, so a routine taught with five runs with what
     * the anchor has.
     */
    private static void lenientQuantityTakesEverything(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = deposit(helper, Items.DIAMOND, 5);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.DIAMOND), 32);

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
                    if (countIn(barrel, Items.DIAMOND) != 32) {
                        helper.fail("expected all 32 to go, the recorded count being incidental; "
                                + "barrel holds " + countIn(barrel, Items.DIAMOND));
                    }
                })
                .thenSucceed();
    }

    /** Quantity on: five means five, and the rest stays home. */
    private static void exactQuantityCapsAtTheRecordedCount(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = deposit(helper, Items.DIAMOND, 5);
        anchor.setPrecision(new TransferPrecision(false, false, true));
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.DIAMOND), 32);

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
                    if (countIn(barrel, Items.DIAMOND) != 5) {
                        helper.fail("expected exactly 5 deposited, barrel holds "
                                + countIn(barrel, Items.DIAMOND));
                    }
                    if (countIn(anchor.getInventory(), Items.DIAMOND) != 27) {
                        helper.fail("the remainder did not come home: anchor holds "
                                + countIn(anchor.getInventory(), Items.DIAMOND) + " of 27");
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------ slot

    /**
     * Slot off: the square was incidental, so an occupied one moves along to the next.
     *
     * <p>The routine puts a log in the barrel's first square, which now holds dirt. The next square
     * along is the same kind of place, so that is where the log goes and the dirt is left alone.
     */
    private static void lenientSlotMovesAlong(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = putALogInAFullSquare(helper);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absolute, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (barrel.getResource(0).getItem() != Items.DIRT) {
                        helper.fail("the occupied square was disturbed: it holds "
                                + barrel.getResource(0).getItem());
                    }
                    if (barrel.getResource(1).getItem() != Items.OAK_LOG) {
                        helper.fail("the log did not move along to the next square: square 1 holds "
                                + barrel.getResource(1).getItem());
                    }
                })
                .thenSucceed();
    }

    /**
     * Slot on: the recorded square, whatever is in it.
     *
     * <p>Same routine, same barrel. Pinned to the square, the click does what a player's click on an
     * occupied square does — it swaps — so the log takes the dirt's place and the dirt comes home in
     * the anchor. Which is the point: the square was the instruction, not the outcome.
     */
    private static void exactSlotRefusesASubstitute(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = putALogInAFullSquare(helper);
        anchor.setPrecision(new TransferPrecision(true, false, false));

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR));

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
                        helper.fail("it moved along to the next square anyway: square 1 holds "
                                + barrel.getResource(1).getItem());
                    }
                    if (countIn(anchor.getInventory(), Items.DIRT) != 64) {
                        helper.fail("the displaced dirt did not come home: anchor holds "
                                + countIn(anchor.getInventory(), Items.DIRT) + " of 64");
                    }
                })
                .thenSucceed();
    }

    /** One log, one click, at a barrel whose first square is already full of something else. */
    private static ChronoAnchorBlockEntity putALogInAFullSquare(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);
        AnchorTestFixture.fillSlot(helper, target, 0, new ItemStack(Items.DIRT, 64));

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new BlockPos(0, 0, -1), CHEST_MENU_SIZE,
                        List.of(new ChronoAction.UseContainer.CarrierSlot(
                                CHEST_MAIN_INVENTORY_START, new ItemStack(Items.OAK_LOG, 1))),
                        List.of(click(CHEST_MAIN_INVENTORY_START, ContainerInput.PICKUP),
                                click(0, ContainerInput.PICKUP)))));
        AnchorTestFixture.unlockAllActions(anchor);
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.OAK_LOG), 1);
        return anchor;
    }

    // ------------------------------------------------------------------- recording

    /**
     * A carrier slot's whole stack survives being imprinted.
     *
     * <p>Imprinting runs the recording through its Codec, and the codec test that would normally
     * cover this cannot build a populated stack without a datapack. So the round trip is asserted
     * here instead, on the field that matters: a name that did not survive would make every
     * item-specific routine unsatisfiable, and it would look like the matching being broken.
     */
    private static void carrierStackSurvivesAnImprint(GameTestHelper helper) {
        ItemStack recorded = new ItemStack(Items.DIAMOND, 5);
        recorded.set(DataComponents.CUSTOM_NAME, Component.literal("Keystone"));

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new BlockPos(0, 0, -1), CHEST_MENU_SIZE,
                        List.of(new ChronoAction.UseContainer.CarrierSlot(
                                CHEST_MAIN_INVENTORY_START, recorded)),
                        List.of(click(CHEST_MAIN_INVENTORY_START, ContainerInput.QUICK_MOVE)))));

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

    // ------------------------------------------------------------------- staging

    /**
     * A session that stocks two squares stocks both of them.
     *
     * <p>The case that went missing: every other container test carries one square, and with one
     * square there is nothing to starve. Not being specific about quantity means taking as much as
     * the anchor has, and taken literally that let the first square empty the stock and every square
     * after it report the routine unstocked — so a two-square session could not run at all, which is
     * most real ones.
     */
    private static void everyCarriedSquareGetsItsShare(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        int first = CHEST_MAIN_INVENTORY_START;
        int second = CHEST_MAIN_INVENTORY_START + 1;

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new BlockPos(0, 0, -1), CHEST_MENU_SIZE,
                        List.of(new ChronoAction.UseContainer.CarrierSlot(
                                        first, new ItemStack(Items.DIAMOND, 2)),
                                new ChronoAction.UseContainer.CarrierSlot(
                                        second, new ItemStack(Items.DIAMOND, 2))),
                        List.of(click(first, ContainerInput.QUICK_MOVE),
                                click(second, ContainerInput.QUICK_MOVE)))));
        AnchorTestFixture.unlockAllActions(anchor);
        // One stack, and the routine wants it split across two squares.
        anchor.getInventoryHandler().set(0, ItemResource.of(Items.DIAMOND), 8);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(target);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NONE) {
                        helper.fail("a two-square session reported " + anchor.getLastFailure().reason()
                                + " with the anchor stocked: the first square took the lot");
                        return;
                    }
                    ResourceHandler<ItemResource> barrel =
                            level.getCapability(Capabilities.Item.BLOCK, absolute, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (countIn(barrel, Items.DIAMOND) != 8) {
                        helper.fail("expected all 8 deposited across both squares, barrel holds "
                                + countIn(barrel, Items.DIAMOND));
                    }
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------- the screen

    /**
     * The path from the checkbox to the anchor.
     *
     * <p>Everything else here sets the flags on the block entity directly, which proves replay reads
     * them and proves nothing whatsoever about whether clicking one ever gets that far. This drives
     * the packet handler with a real player and a real open menu, which is every link in that chain
     * except the click itself.
     */
    @SuppressWarnings("removal")
    private static void theScreenCanSetIt(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE), player);

        player.openMenu(anchor);

        TransferPrecision wanted = new TransferPrecision(true, false, true);
        if (!AnchorPrecisionPayload.apply(player, anchor.getBlockPos(), wanted.pack())) {
            helper.fail("the anchor refused a setting from the player who has its screen open");
            return;
        }
        if (!anchor.getPrecision().equals(wanted)) {
            helper.fail("expected " + wanted + ", anchor holds " + anchor.getPrecision());
            return;
        }

        // And the gate is real: the same packet with no menu open changes nothing. Without this the
        // test above would pass just as well against a handler that accepted anything.
        player.containerMenu = player.inventoryMenu;
        if (AnchorPrecisionPayload.apply(player, anchor.getBlockPos(), 0)) {
            helper.fail("the anchor accepted a setting from a player with no menu open");
            return;
        }
        if (!anchor.getPrecision().equals(wanted)) {
            helper.fail("the refused packet changed the setting anyway");
            return;
        }
        helper.succeed();
    }

    /** An anchor somebody else imprinted is not yours to retune, screen open or not. */
    @SuppressWarnings("removal")
    private static void aStrangerCannotSetIt(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));

        // placeAndImprint takes ownership as OWNER_ID; the mock player is somebody else entirely.
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        stranger.openMenu(anchor);

        if (AnchorPrecisionPayload.apply(stranger, anchor.getBlockPos(),
                new TransferPrecision(true, true, true).pack())) {
            helper.fail("a stranger retuned somebody else's anchor");
            return;
        }
        if (!anchor.getPrecision().equals(TransferPrecision.NONE)) {
            helper.fail("the anchor's setting changed anyway: " + anchor.getPrecision());
            return;
        }
        helper.succeed();
    }

    /**
     * The setting survives the anchor being saved and loaded again.
     *
     * <p>A setting that did not persist would look exactly like one that never applied: you tick a
     * box, walk away, the chunk unloads, and the anchor goes back to what it was. That is
     * indistinguishable from the packet never arriving, and it is the difference between a bug in
     * the GUI and a bug in the block entity.
     */
    private static void settingSurvivesASaveAndLoad(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));

        TransferPrecision wanted = new TransferPrecision(true, false, true);
        anchor.setPrecision(wanted);

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag saved = anchor.saveCustomOnly(registries);

        ChronoAnchorBlockEntity reloaded =
                new ChronoAnchorBlockEntity(anchor.getBlockPos(), anchor.getBlockState());
        reloaded.loadCustomOnly(
                TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));

        if (!reloaded.getPrecision().equals(wanted)) {
            helper.fail("the setting did not survive a save and load: saved " + wanted
                    + ", loaded " + reloaded.getPrecision());
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------- helpers

    /** A routine that shift-clicks {@code count} of {@code item} out of the carrier and into a barrel. */
    private static ChronoAnchorBlockEntity deposit(GameTestHelper helper, Item item, int count) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new BlockPos(0, 0, -1), CHEST_MENU_SIZE,
                        List.of(new ChronoAction.UseContainer.CarrierSlot(
                                CHEST_MAIN_INVENTORY_START, new ItemStack(item, count))),
                        List.of(click(CHEST_MAIN_INVENTORY_START, ContainerInput.QUICK_MOVE)))));
        AnchorTestFixture.unlockAllActions(anchor);
        return anchor;
    }

    private static ChronoAction.UseContainer.Click click(int slot, ContainerInput input) {
        return new ChronoAction.UseContainer.Click(slot, LEFT, input);
    }
}
