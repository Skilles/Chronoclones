package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.RedstoneStatus;
import com.skilles.chronoclones.block.RunState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class RedstoneGameTest {

    private RedstoneGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("a_redstone_pulse_runs_one_cycle_and_stops",
                RedstoneGameTest::pulseRunsOneCycle);
        ChronoclonesGameTests.add("a_held_signal_keeps_the_routine_looping",
                RedstoneGameTest::heldSignalKeepsLooping);
        ChronoclonesGameTests.add("losing_the_signal_lets_the_cycle_finish_first",
                RedstoneGameTest::lostSignalFinishesTheCycle);
        ChronoclonesGameTests.add("an_anchor_told_to_ignore_redstone_ignores_it",
                RedstoneGameTest::ignoringAnchorIgnores);
        ChronoclonesGameTests.add("a_comparator_reads_running_and_stopped_apart",
                RedstoneGameTest::comparatorReadsState);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);
    private static final BlockPos POWER = ANCHOR.east();

    private static ChronoAnchorBlockEntity obedientStoppedAnchor(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.setRunState(RunState.STOPPED);
        anchor.setObeysRedstone(true);
        return anchor;
    }

    private static void power(GameTestHelper helper, boolean on) {
        helper.setBlock(POWER, on ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
    }

    private static void pulseRunsOneCycle(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = obedientStoppedAnchor(helper);

        helper.startSequence()
                .thenExecuteAfter(1, () -> power(helper, true))
                .thenExecuteAfter(2, () -> {
                    if (anchor.getRunState() != RunState.RUNNING) {
                        helper.fail("a rising edge did not start the routine");
                    }
                })
                .thenExecuteAfter(2, () -> power(helper, false))
                // The 20-tick cycle ends and the anchor puts itself away.
                .thenExecuteAfter(40, () -> {
                    if (anchor.getRunState() != RunState.STOPPED) {
                        helper.fail("a pulse left the routine " + anchor.getRunState()
                                + " instead of stopping after its cycle");
                    }
                })
                .thenSucceed();
    }

    private static void heldSignalKeepsLooping(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = obedientStoppedAnchor(helper);

        helper.startSequence()
                .thenExecuteAfter(1, () -> power(helper, true))
                // Well past one 20-tick cycle: still looping.
                .thenExecuteAfter(50, () -> {
                    if (anchor.getRunState() != RunState.RUNNING) {
                        helper.fail("a held signal let the routine wind down to "
                                + anchor.getRunState());
                    }
                })
                .thenSucceed();
    }

    private static void lostSignalFinishesTheCycle(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = obedientStoppedAnchor(helper);

        helper.startSequence()
                .thenExecuteAfter(1, () -> power(helper, true))
                .thenExecuteAfter(5, () -> power(helper, false))
                .thenExecuteAfter(1, () -> {
                    if (anchor.getRunState() != RunState.RUNNING) {
                        helper.fail("losing the signal cut the routine off mid-cycle");
                    }
                })
                .thenExecuteAfter(40, () -> {
                    if (anchor.getRunState() != RunState.STOPPED) {
                        helper.fail("the routine never stopped after its farewell cycle");
                    }
                })
                .thenSucceed();
    }

    private static void ignoringAnchorIgnores(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.setRunState(RunState.STOPPED);

        helper.startSequence()
                .thenExecuteAfter(1, () -> power(helper, true))
                .thenExecuteAfter(5, () -> {
                    if (anchor.getRunState() != RunState.STOPPED) {
                        helper.fail("an anchor told to ignore redstone started anyway");
                    }
                })
                .thenSucceed();
    }

    private static void comparatorReadsState(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = obedientStoppedAnchor(helper);

        if (signal(helper) != RedstoneStatus.STOPPED) {
            helper.fail("a stopped anchor reads " + signal(helper) + " instead of "
                    + RedstoneStatus.STOPPED);
        }

        helper.startSequence()
                .thenExecuteAfter(1, () -> power(helper, true))
                .thenExecuteAfter(3, () -> {
                    if (signal(helper) != RedstoneStatus.RUNNING) {
                        helper.fail("a running anchor reads " + signal(helper) + " instead of "
                                + RedstoneStatus.RUNNING);
                    }
                    anchor.setRunState(RunState.PAUSED);
                    if (signal(helper) != RedstoneStatus.PAUSED) {
                        helper.fail("a paused anchor reads " + signal(helper) + " instead of "
                                + RedstoneStatus.PAUSED);
                    }
                })
                .thenSucceed();
    }

    private static int signal(GameTestHelper helper) {
        return helper.getBlockState(ANCHOR).getAnalogOutputSignal(
                helper.getLevel(), helper.absolutePos(ANCHOR), Direction.NORTH);
    }
}
