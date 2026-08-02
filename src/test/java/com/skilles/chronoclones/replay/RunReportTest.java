package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunReportTest {

    @Test
    @DisplayName("a success is reported as run")
    void successReadsAsOk() {
        RunReport report = new RunReport();
        report.resize(1);

        report.record(0, 0, 100L, ActionResult.OK);

        assertEquals(RunReport.Outcome.OK, report.entry(0).outcome());
    }

    @Test
    @DisplayName("a soft failure is reported as skipped, a halting one as halted")
    void failuresSplitBySeverity() {
        RunReport report = new RunReport();
        report.resize(2);

        report.record(0, 0, 100L, ActionResult.fail(FailureReason.NO_BLOCK, BlockPos.ZERO));
        report.record(1, 0, 100L, ActionResult.fail(FailureReason.NO_CHARGE, BlockPos.ZERO));

        assertEquals(RunReport.Outcome.SKIPPED, report.entry(0).outcome());
        assertEquals(RunReport.Outcome.HALTED, report.entry(1).outcome());
    }

    @Test
    @DisplayName("an action nobody reached yet is pending")
    void unreachedIsPending() {
        RunReport report = new RunReport();
        report.resize(3);

        report.record(0, 0, 100L, ActionResult.OK);

        assertEquals(RunReport.Outcome.PENDING, report.entry(1).outcome());
        assertEquals(RunReport.Outcome.PENDING, report.entry(2).outcome());
    }

    @Test
    @DisplayName("the latest attempt wins")
    void latestAttemptWins() {
        RunReport report = new RunReport();
        report.resize(1);

        report.record(0, 0, 100L, ActionResult.fail(FailureReason.NO_BLOCK, BlockPos.ZERO));
        report.record(0, 1, 140L, ActionResult.OK);

        assertEquals(RunReport.Outcome.OK, report.entry(0).outcome());
        assertEquals(1, report.entry(0).cloneIndex());
        assertEquals(140L, report.entry(0).gameTime());
    }

    @Test
    @DisplayName("resizing to the same length still starts the report over")
    void resizeAlwaysResets() {
        RunReport report = new RunReport();
        report.resize(1);
        report.record(0, 0, 100L, ActionResult.OK);

        report.resize(1);

        assertEquals(RunReport.Outcome.PENDING, report.entry(0).outcome());
    }

    @Test
    @DisplayName("a failing container step keeps its index")
    void stepSurvives() {
        RunReport report = new RunReport();
        report.resize(1);

        report.record(0, 0, 100L,
                ActionResult.fail(FailureReason.NO_SLOT, BlockPos.ZERO, 3));

        assertEquals(3, report.entry(0).step());
    }

    @Test
    @DisplayName("counts tally each outcome")
    void countsTally() {
        RunReport report = new RunReport();
        report.resize(4);

        report.record(0, 0, 100L, ActionResult.OK);
        report.record(1, 0, 100L, ActionResult.OK);
        report.record(2, 0, 100L, ActionResult.fail(FailureReason.REFUSED, BlockPos.ZERO));

        assertEquals(2, report.count(RunReport.Outcome.OK));
        assertEquals(1, report.count(RunReport.Outcome.SKIPPED));
        assertEquals(1, report.count(RunReport.Outcome.PENDING));
        assertEquals(0, report.count(RunReport.Outcome.HALTED));
    }

    @Test
    @DisplayName("recording outside the report is ignored, not an error")
    void outOfBoundsIsIgnored() {
        RunReport report = new RunReport();
        report.resize(1);

        report.record(5, 0, 100L, ActionResult.OK);
        report.record(-1, 0, 100L, ActionResult.OK);

        assertEquals(RunReport.Outcome.PENDING, report.entry(0).outcome());
        assertEquals(RunReport.Outcome.PENDING, report.entry(5).outcome());
    }
}
