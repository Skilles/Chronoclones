package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * End-to-end replay behaviour, focused on the guarantees that are easy to regress and impossible to
 * assert off-runtime — anything involving real block state, real loot tables, or the inventory.
 */
final class ReplayGameTest {

    private ReplayGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("break_stores_drops_in_anchor", ReplayGameTest::breakStoresDropsInAnchor);
        ChronoclonesGameTests.add("blacklisted_block_survives", ReplayGameTest::blacklistedBlockSurvives);
        ChronoclonesGameTests.add("carries_on_when_the_block_changed",
                ReplayGameTest::carriesOnWhenTheBlockChanged);
        ChronoclonesGameTests.add("full_inventory_does_not_destroy", ReplayGameTest::fullInventoryDoesNotDestroy);
        ChronoclonesGameTests.add("block_entities_are_never_broken", ReplayGameTest::blockEntitiesAreNeverBroken);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** The basic loop: the routine runs, the block goes, and its drops land in the anchor. */
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

    /** the blacklist is enforced at execute time, whatever the recording claims. */
    private static void blacklistedBlockSurvives(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BEDROCK);

        // The recording expects bedrock, so only the blacklist can stop this.
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

    /**
     * The default carries on when the world has drifted: the square holds a different block now, and
     * the routine mines it and keeps its drops.
     *
     * <p>This is the everyday case the whole rule exists for, which is why it sits here with the rest
     * of the end-to-end behaviour rather than among the matching rules. The refusal half — an anchor
     * with an Chrono Lens leaving it alone — is in {@code CoherenceGameTest}.
     */
    private static void carriesOnWhenTheBlockChanged(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        // The canonical drift: you recorded against stone and it is cobblestone now.
        helper.setBlock(target, Blocks.COBBLESTONE);

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockNotPresent(Blocks.COBBLESTONE, target);
                    // The drops prove it was mined rather than merely replaced. The diagnostic is
                    // not asserted: a one-action routine loops every second, so by now it has come
                    // round again and is correctly reporting nothing left to break.
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.COBBLESTONE) != 1) {
                        helper.fail("expected the drop of the block that was actually there");
                    }
                })
                .thenSucceed();
    }

    /**
     * The deliberate deviation from asserted.
     *
     * <p>The spec breaks the block and then halts if the drops do not fit. Drops are instead
     * inserted transactionally first, so a full anchor leaves the block standing rather than
     * destroying something it cannot store. This test is the reason that ordering is safe to keep.
     */
    private static void fullInventoryDoesNotDestroy(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        // Fill every storage slot with something that cannot merge with cobblestone.
        var inventory = anchor.getInventoryHandler();
        for (int slot = 0; slot < inventory.size(); slot++) {
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

    /** block entities are refused outright in this pass. */
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
