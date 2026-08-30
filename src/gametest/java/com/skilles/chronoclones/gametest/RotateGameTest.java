package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class RotateGameTest {

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private RotateGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("rotate_turns_where_the_routine_acts", RotateGameTest::rotateTurnsTheTarget);
        ChronoclonesGameTests.add("rotate_after_nudge_spins_in_place", RotateGameTest::rotateSpinsAroundTheOrigin);
        ChronoclonesGameTests.add("reset_clears_the_rotation", RotateGameTest::resetClearsTheRotation);
    }

    /** The fixture anchor faces north and breaks the block one step in front of it. */
    private static void rotateTurnsTheTarget(GameTestHelper helper) {
        BlockPos recorded = AnchorTestFixture.targetOf(ANCHOR);
        BlockPos rotated = ANCHOR.east();
        helper.setBlock(recorded, Blocks.STONE);
        helper.setBlock(rotated, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.rotateOrigin(1);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertBlockPresent(Blocks.AIR, rotated);
                    helper.assertBlockPresent(Blocks.STONE, recorded);
                })
                .thenSucceed();
    }

    /** The offset stays in the anchor's own space: rotating pivots around the nudged origin. */
    private static void rotateSpinsAroundTheOrigin(GameTestHelper helper) {
        BlockPos expected = ANCHOR.west();
        helper.setBlock(expected, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.nudgeOrigin(new BlockPos(-2, 0, 0));
        anchor.rotateOrigin(1);

        helper.startSequence()
                .thenExecuteAfter(15, () -> helper.assertBlockPresent(Blocks.AIR, expected))
                .thenSucceed();
    }

    private static void resetClearsTheRotation(GameTestHelper helper) {
        BlockPos recorded = AnchorTestFixture.targetOf(ANCHOR);
        BlockPos rotated = ANCHOR.east();
        helper.setBlock(recorded, Blocks.STONE);
        helper.setBlock(rotated, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.rotateOrigin(1);
        anchor.resetOrigin();

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertBlockPresent(Blocks.AIR, recorded);
                    helper.assertBlockPresent(Blocks.STONE, rotated);
                })
                .thenSucceed();
    }
}
