package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;
import com.skilles.chronoclones.replay.RunReport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

final class ReportGameTest {

    private ReportGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("a_finished_action_is_reported_as_run",
                ReportGameTest::finishedActionReadsAsRun);
        ChronoclonesGameTests.add("a_missing_block_is_reported_skipped_with_its_reason",
                ReportGameTest::missingBlockReadsAsSkipped);
        ChronoclonesGameTests.add("a_halt_leaves_the_rest_marked_not_reached",
                ReportGameTest::haltLeavesTheRestPending);
        ChronoclonesGameTests.add("a_container_step_that_fails_names_its_step",
                ReportGameTest::failingStepNamesItself);
        ChronoclonesGameTests.add("the_report_names_the_clone_that_tried",
                ReportGameTest::reportNamesTheClone);
    }

    private static void reportNamesTheClone(GameTestHelper helper) {
        // No stone at the target, so every clone that reaches the break records the same skip.
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.getUpgradeHandler().set(0, net.neoforged.neoforge.transfer.item.ItemResource.of(
                com.skilles.chronoclones.registry.ModItems.CHRONO_SPLITTER.get()), 1);
        anchor.serverTick();

        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    RunReport.Entry entry = anchor.getRunReport().entry(0);
                    if (entry.outcome() != RunReport.Outcome.SKIPPED) {
                        helper.fail("two clones ran a cycle and the report still says "
                                + entry.outcome());
                        return;
                    }
                    int clones = anchor.getUpgrades().cloneCount();
                    if (entry.cloneIndex() < 0 || entry.cloneIndex() >= clones) {
                        helper.fail("the report blames clone " + entry.cloneIndex()
                                + " of " + clones);
                    }
                })
                .thenSucceed();
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static void finishedActionReadsAsRun(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        // Asserted mid-cycle: the next loop finds the block already gone and overwrites the
        // entry with that skip, which is the rolling report doing its job.
        helper.startSequence()
                .thenExecuteAfter(12, () -> {
                    RunReport.Entry entry = anchor.getRunReport().entry(0);
                    if (entry.outcome() != RunReport.Outcome.OK) {
                        helper.fail("a break that finished reads as " + entry.outcome()
                                + " instead of OK");
                    }
                })
                .thenSucceed();
    }

    private static void missingBlockReadsAsSkipped(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    RunReport.Entry entry = anchor.getRunReport().entry(0);
                    if (entry.outcome() != RunReport.Outcome.SKIPPED) {
                        helper.fail("a break with nothing to break reads as " + entry.outcome()
                                + " instead of SKIPPED");
                    }
                    if (entry.reason() != FailureReason.NO_BLOCK) {
                        helper.fail("the skip kept " + entry.reason()
                                + " as its reason instead of NO_BLOCK");
                    }
                })
                .thenSucceed();
    }

    private static void haltLeavesTheRestPending(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprintUnfueled(
                helper, ANCHOR, AnchorTestFixture.routine(List.of(
                        new ChronoAction.BreakBlock(new BlockPos(0, 0, -1),
                                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE),
                                new net.minecraft.world.item.ItemStack(Items.NETHERITE_PICKAXE)),
                        new ChronoAction.BreakBlock(new BlockPos(1, 0, -1),
                                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE),
                                new net.minecraft.world.item.ItemStack(Items.NETHERITE_PICKAXE)))));

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    RunReport report = anchor.getRunReport();
                    if (report.entry(0).outcome() != RunReport.Outcome.HALTED) {
                        helper.fail("an unfueled anchor's first action reads as "
                                + report.entry(0).outcome() + " instead of HALTED");
                    }
                    if (report.entry(1).outcome() != RunReport.Outcome.PENDING) {
                        helper.fail("the action after the halt reads as "
                                + report.entry(1).outcome() + " instead of PENDING");
                    }
                })
                .thenSucceed();
    }

    private static void failingStepNamesItself(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new MenuTarget.Block(new BlockPos(0, 0, -1)), 27 + 36, List.of(),
                        List.of(
                                new SessionStep.RawClick(0, 0, ContainerInput.PICKUP),
                                new SessionStep.RawClick(27 + 36 + 5, 0, ContainerInput.PICKUP)))));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    RunReport.Entry entry = anchor.getRunReport().entry(0);
                    if (entry.outcome() != RunReport.Outcome.SKIPPED) {
                        helper.fail("a container action with a bad step reads as " + entry.outcome()
                                + " instead of SKIPPED");
                    }
                    if (entry.step() != 1) {
                        helper.fail("the report names step " + entry.step()
                                + " instead of the failing step 1");
                    }
                })
                .thenSucceed();
    }
}
