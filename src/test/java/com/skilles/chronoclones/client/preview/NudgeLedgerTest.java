package com.skilles.chronoclones.client.preview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NudgeLedgerTest {

    @Test
    @DisplayName("a reply with no nudges in between speaks for the origin")
    void quietReplySpeaks() {
        NudgeLedger ledger = new NudgeLedger();
        ledger.asked();

        assertTrue(ledger.replyKnowsTheOrigin());
    }

    @Test
    @DisplayName("a nudge after the question makes the answer stale")
    void nudgeAfterAskingGoesStale() {
        NudgeLedger ledger = new NudgeLedger();
        ledger.asked();
        ledger.nudged();

        assertFalse(ledger.replyKnowsTheOrigin(),
                "the request left before the nudge, so its answer flashes the preview back");
    }

    @Test
    @DisplayName("a nudge before the question is already part of the answer")
    void nudgeBeforeAskingIsFine() {
        NudgeLedger ledger = new NudgeLedger();
        ledger.nudged();
        ledger.asked();

        assertTrue(ledger.replyKnowsTheOrigin());
    }

    @Test
    @DisplayName("asking again catches the ledger up")
    void askingAgainCatchesUp() {
        NudgeLedger ledger = new NudgeLedger();
        ledger.asked();
        ledger.nudged();
        ledger.nudged();
        ledger.asked();

        assertTrue(ledger.replyKnowsTheOrigin());
    }
}
