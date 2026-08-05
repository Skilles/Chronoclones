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
        ChronoclonesGameTests.add("the_redstone_mode_and_latch_survive_a_reload",
                RedstoneGameTest::latchSurvivesAReload);
        ChronoclonesGameTests.add("standing_power_read_again_after_a_reload_is_not_an_edge",
                RedstoneGameTest::standingPowerIsNotAnEdge);
        ChronoclonesGameTests.add("a_starved_anchor_reads_stalled_on_a_comparator",
                RedstoneGameTest::starvedAnchorReadsStalled);
        ChronoclonesGameTests.add("a_wind_down_reads_below_full_on_a_comparator",
                RedstoneGameTest::windDownReadsBelowFull);
        ChronoclonesGameTests.add("a_rising_edge_resumes_a_paused_anchor",
                RedstoneGameTest::risingEdgeResumesFromPause);
        ChronoclonesGameTests.add("a_real_comparator_hears_about_the_wind_down",
                RedstoneGameTest::realComparatorHearsTheWindDown);
    }

    /** Through an actual comparator block, not getAnalogOutputSignal: a direct read cannot
     * tell whether the anchor remembered to announce the change. */
    private static void realComparatorHearsTheWindDown(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = obedientStoppedAnchor(helper);
        // North of the anchor is the routine's target; power sits east, so the comparator
        // reads from the west with its facing pointed at the anchor.
        BlockPos comparatorPos = ANCHOR.west();
        // A diode pops without sturdy ground, whatever the plot floor happens to be.
        helper.setBlock(comparatorPos.below(), Blocks.STONE);
        helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.EAST));

        helper.startSequence()
                .thenExecuteAfter(1, () -> power(helper, true))
                .thenExecuteAfter(4, () -> {
                    if (comparatorOutput(helper, comparatorPos) != RedstoneStatus.RUNNING) {
                        helper.fail("a comparator beside a running anchor outputs "
                                + comparatorOutput(helper, comparatorPos)
                                + " instead of " + RedstoneStatus.RUNNING);
                    }
                })
                .thenExecuteAfter(1, () -> power(helper, false))
                .thenExecuteAfter(4, () -> {
                    if (comparatorOutput(helper, comparatorPos) != RedstoneStatus.FINISHING) {
                        helper.fail("the anchor began its farewell cycle and the comparator"
                                + " still outputs " + comparatorOutput(helper, comparatorPos)
                                + ": the wind-down was never announced");
                    }
                })
                .thenSucceed();
    }

    private static int comparatorOutput(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative))
                instanceof net.minecraft.world.level.block.entity.ComparatorBlockEntity comparator
                ? comparator.getOutputSignal()
                : -1;
    }

    private static ChronoAnchorBlockEntity reloaded(GameTestHelper helper,
                                                    ChronoAnchorBlockEntity anchor) {
        //? if >=26 {
        net.minecraft.world.level.storage.TagValueOutput output =
                net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING,
                        helper.getLevel().registryAccess());
        anchor.saveWithoutMetadata(output);

        ChronoAnchorBlockEntity fresh = new ChronoAnchorBlockEntity(
                anchor.getBlockPos(), anchor.getBlockState());
        fresh.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), output.buildResult()));
        return fresh;
        //?} else {
        /*net.minecraft.nbt.CompoundTag saved =
                anchor.saveWithoutMetadata(helper.getLevel().registryAccess());

        ChronoAnchorBlockEntity fresh = new ChronoAnchorBlockEntity(
                anchor.getBlockPos(), anchor.getBlockState());
        fresh.loadWithComponents(saved, helper.getLevel().registryAccess());
        return fresh;
        *///?}
    }

    private static void latchSurvivesAReload(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = obedientStoppedAnchor(helper);
        anchor.onRedstoneSignal(true);
        anchor.onRedstoneSignal(false);

        ChronoAnchorBlockEntity loaded = reloaded(helper, anchor);
        if (!loaded.obeysRedstone()) {
            helper.fail("the redstone mode did not survive the reload");
            return;
        }
        if (loaded.comparatorSignal() != RedstoneStatus.FINISHING) {
            helper.fail("the farewell cycle read " + loaded.comparatorSignal()
                    + " after a reload instead of " + RedstoneStatus.FINISHING
                    + ": the finishing latch was lost and the routine would loop forever");
            return;
        }
        helper.succeed();
    }

    private static void standingPowerIsNotAnEdge(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = obedientStoppedAnchor(helper);
        anchor.onRedstoneSignal(true);
        anchor.setRunState(RunState.STOPPED);

        ChronoAnchorBlockEntity loaded = reloaded(helper, anchor);
        loaded.onRedstoneSignal(true);
        if (loaded.getRunState() != RunState.STOPPED) {
            helper.fail("power that never went away started the routine again after a reload");
            return;
        }

        loaded.onRedstoneSignal(false);
        loaded.onRedstoneSignal(true);
        if (loaded.getRunState() != RunState.RUNNING) {
            helper.fail("a genuine fresh edge after the reload was ignored");
            return;
        }
        helper.succeed();
    }

    private static void starvedAnchorReadsStalled(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        AnchorTestFixture.placeAndImprintUnfueled(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (signal(helper) != RedstoneStatus.STALLED) {
                        helper.fail("an anchor with no charge reads " + signal(helper)
                                + " instead of " + RedstoneStatus.STALLED);
                    }
                })
                .thenSucceed();
    }

    private static void windDownReadsBelowFull(GameTestHelper helper) {
        obedientStoppedAnchor(helper);

        helper.startSequence()
                .thenExecuteAfter(1, () -> power(helper, true))
                .thenExecuteAfter(3, () -> power(helper, false))
                .thenExecuteAfter(2, () -> {
                    if (signal(helper) != RedstoneStatus.FINISHING) {
                        helper.fail("an anchor playing its farewell cycle reads " + signal(helper)
                                + " instead of " + RedstoneStatus.FINISHING);
                    }
                })
                .thenSucceed();
    }

    private static void risingEdgeResumesFromPause(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = obedientStoppedAnchor(helper);

        helper.startSequence()
                .thenExecuteAfter(1, () -> power(helper, true))
                .thenExecuteAfter(2, () -> anchor.setRunState(RunState.PAUSED))
                .thenExecuteAfter(2, () -> power(helper, false))
                .thenExecuteAfter(2, () -> power(helper, true))
                .thenExecuteAfter(2, () -> {
                    if (anchor.getRunState() != RunState.RUNNING) {
                        helper.fail("a fresh edge left a paused anchor " + anchor.getRunState());
                    }
                })
                .thenSucceed();
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
        //? if >=26 {
        return helper.getBlockState(ANCHOR).getAnalogOutputSignal(
                helper.getLevel(), helper.absolutePos(ANCHOR), Direction.NORTH);
        //?} else {
        /*return helper.getBlockState(ANCHOR).getAnalogOutputSignal(
                helper.getLevel(), helper.absolutePos(ANCHOR));
        *///?}
    }
}
