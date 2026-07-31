package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Moving a routine's origin, and the one thing that must not move with it.
 */
final class NudgeGameTest {

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private NudgeGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("nudge_moves_where_the_routine_acts", NudgeGameTest::nudgeMovesTheTarget);
        ChronoclonesGameTests.add("nudge_cannot_extend_reach", NudgeGameTest::nudgeCannotExtendReach);
    }

    /** The routine breaks one block further along, and leaves the original alone. */
    private static void nudgeMovesTheTarget(GameTestHelper helper) {
        BlockPos recorded = AnchorTestFixture.targetOf(ANCHOR);
        BlockPos nudged = recorded.west();
        helper.setBlock(recorded, Blocks.STONE);
        helper.setBlock(nudged, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));
        // The anchor faces north, so local space is world space and west is -X.
        anchor.nudgeOrigin(new BlockPos(-1, 0, 0));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertBlockPresent(Blocks.AIR, nudged);
                    helper.assertBlockPresent(Blocks.STONE, recorded);
                })
                .thenSucceed();
    }

    /**
     * An offset past the radius makes a routine fail, rather than making it reach further.
     */
    private static void nudgeCannotExtendReach(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));

        // A refusal needs no block to refuse at.
        int beyond = ChronoclonesConfig.MAX_RADIUS.getAsInt() + 4;
        anchor.nudgeOrigin(new BlockPos(0, 0, -beyond));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    DiagnosticState.FailureReason reason = anchor.getLastFailure().reason();
                    if (reason != DiagnosticState.FailureReason.OUT_OF_RANGE) {
                        helper.fail("an offset past MAX_RADIUS must refuse, not reach: got " + reason
                                + ". The radius is being measured from the origin, not the anchor.");
                    }
                })
                .thenSucceed();
    }

}
