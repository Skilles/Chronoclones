package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ActionSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

final class ReplayGameTest {

    private ReplayGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("break_stores_drops_in_anchor", ReplayGameTest::breakStoresDropsInAnchor);
        ChronoclonesGameTests.add("blacklisted_block_survives", ReplayGameTest::blacklistedBlockSurvives);
        ChronoclonesGameTests.add("carries_on_when_the_block_changed",
                ReplayGameTest::carriesOnWhenTheBlockChanged);
        ChronoclonesGameTests.add("break_refuses_a_block_it_was_not_recorded_on",
                ReplayGameTest::refusesABlockItWasNotRecordedOn);
        ChronoclonesGameTests.add("full_inventory_does_not_destroy", ReplayGameTest::fullInventoryDoesNotDestroy);
        ChronoclonesGameTests.add("block_entities_are_never_broken", ReplayGameTest::blockEntitiesAreNeverBroken);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static void breakStoresDropsInAnchor(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockNotPresent(Blocks.STONE, target);

                    if (countOf(anchor.getInventory(), Items.COBBLESTONE) == 0) {
                        helper.fail("stone was broken but its drop never reached the anchor inventory");
                    }
                })
                .thenSucceed();
    }

    private static void blacklistedBlockSurvives(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BEDROCK);

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.BEDROCK));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockPresent(Blocks.BEDROCK, target);

                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.BLACKLISTED) {
                        helper.fail("expected BLACKLISTED, got " + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void carriesOnWhenTheBlockChanged(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.COBBLESTONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE)
                        .withSettings(0, ActionSettings.DEFAULT.withRecordedSubject(false)));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockNotPresent(Blocks.COBBLESTONE, target);
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.COBBLESTONE) != 1) {
                        helper.fail("expected the drop of the block that was actually there");
                    }
                })
                .thenSucceed();
    }

    private static void refusesABlockItWasNotRecordedOn(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.COBBLESTONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockPresent(Blocks.COBBLESTONE, target);
                    if (anchor.getLastFailure().reason()
                            != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("expected WRONG_BLOCK for a square holding something else, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void fullInventoryDoesNotDestroy(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        var inventory = anchor.getCloneInventory(0);
        for (int slot = 0; slot < inventory.size() - 1; slot++) {
            inventory.set(slot, ItemResource.of(Items.BEDROCK), 64);
        }

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockPresent(Blocks.STONE, target);

                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.INVENTORY_FULL) {
                        helper.fail("expected INVENTORY_FULL, got " + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void blockEntitiesAreNeverBroken(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.CHEST);

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.CHEST));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockPresent(Blocks.CHEST, target);

                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.BLACKLISTED) {
                        helper.fail("expected BLACKLISTED for a block entity, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static int countOf(ResourceHandler<ItemResource> handler, net.minecraft.world.item.Item item) {
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
