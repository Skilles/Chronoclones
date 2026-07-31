package com.skilles.chronoclones.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sharing one tick's actions between anchors.
 *
 * <p>The property that matters is the one a single counter did not have: an anchor that ticks last
 * still gets to act, however greedy the ones before it are.
 */
class ActionBudgetTest {

    @Test
    @DisplayName("a lone anchor gets the whole budget")
    void loneAnchorGetsEverything() {
        ActionBudget budget = new ActionBudget(64);
        budget.reset(64);

        int claimed = 0;
        while (budget.claim("only")) {
            claimed++;
        }
        assertEquals(64, claimed);
    }

    @Test
    @DisplayName("a greedy anchor cannot starve one that ticks after it")
    void greedyAnchorCannotStarveAnother() {
        ActionBudget budget = twoActiveAnchors(64);

        // The first anchor takes everything it possibly can, as it would every tick.
        int greedy = 0;
        while (budget.claim("first")) {
            greedy++;
        }

        // The second is asked afterwards, which under one shared counter was always too late.
        assertTrue(budget.claim("second"),
                "the second anchor got nothing after the first took " + greedy);
    }

    @Test
    @DisplayName("the guaranteed floor is a share of half the budget, not half the budget")
    void allowanceIsAFloorNotAQuota() {
        ActionBudget budget = twoActiveAnchors(64);
        // Half of the budget is reserved as floors and split between the two; the rest is pooled.
        assertEquals(16, budget.allowance());
        assertEquals(32, budget.overflow());
    }

    @Test
    @DisplayName("a busy anchor may take the pool the idle ones never asked for")
    void oneBusyAnchorMayUseThePool() {
        ActionBudget budget = twoActiveAnchors(64);

        int busy = 0;
        while (budget.claim("first")) {
            busy++;
        }
        // Its own floor plus the whole shared pool: only the other anchor's floor is withheld.
        assertEquals(48, busy);

        // And that floor is still there for the anchor it was reserved for.
        int quiet = 0;
        while (budget.claim("second")) {
            quiet++;
        }
        assertEquals(16, quiet);
    }

    @Test
    @DisplayName("more anchors than budget still means one action each")
    void everybodyGetsAtLeastOne() {
        ActionBudget budget = new ActionBudget(4);
        for (int anchor = 0; anchor < 10; anchor++) {
            budget.claim("anchor" + anchor);
        }
        budget.reset(4);

        assertEquals(1, budget.allowance(), "an allowance of nothing is not a share");
        for (int anchor = 0; anchor < 10; anchor++) {
            assertTrue(budget.claim("anchor" + anchor),
                    "anchor " + anchor + " was refused its one action");
        }
    }

    @Test
    @DisplayName("the total is never exceeded once every anchor is asking")
    void neverSpendsMoreThanTheTotal() {
        ActionBudget budget = twoActiveAnchors(64);

        int claimed = 0;
        boolean progress = true;
        while (progress) {
            progress = false;
            for (String anchor : new String[] {"first", "second"}) {
                if (budget.claim(anchor)) {
                    claimed++;
                    progress = true;
                }
            }
        }
        assertEquals(64, claimed);
        assertFalse(budget.claim("first"));
    }

    @Test
    @DisplayName("a budget of nothing refuses everybody rather than throwing")
    void zeroBudgetRefuses() {
        ActionBudget budget = new ActionBudget(0);
        budget.reset(0);
        // The allowance floor is one, so a level configured to zero still ticks over rather than
        // dividing by nothing; what it must not do is fail.
        assertTrue(budget.total() == 0);
        budget.claim("anchor");
    }

    /**
     * A budget that has already seen two anchors, so the next tick plans for two.
     *
     * <p>The population is last tick's: an anchor is counted from the tick after it first asks.
     */
    private static ActionBudget twoActiveAnchors(int total) {
        ActionBudget budget = new ActionBudget(total);
        budget.claim("first");
        budget.claim("second");
        budget.reset(total);
        assertEquals(2, budget.expected());
        return budget;
    }
}
