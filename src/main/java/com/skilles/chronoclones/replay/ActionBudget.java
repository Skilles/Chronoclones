package com.skilles.chronoclones.replay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One tick's worth of clone actions, shared out so that every anchor gets a turn.
 *
 * <p>This used to be a single counter. Block entities tick in a fixed order, so the anchors near the
 * front of it could spend the whole budget before the ones behind them were asked, every tick, for
 * as long as the level stayed loaded -- a farm that worked perfectly alone would simply stop when
 * its owner built a second one to the north of it.
 *
 * <p>So each anchor gets a small guaranteed allowance first, and only competes for the shared pool
 * once it has used it. The allowance is a floor rather than a quota: an anchor with work to do can
 * still take most of the level's budget when the others are idle, and one that always ticks last
 * can still count on its floor when they are not.
 *
 * <p>No Minecraft types: this is arithmetic, and arithmetic is worth being able to test directly.
 */
public final class ActionBudget {

    /** How many actions the level may run this tick, in total. */
    private int total;

    /** What each anchor may spend before it has to start competing. */
    private int allowance;

    /** What is left of the budget once every allowance is accounted for. */
    private int overflow;

    /** What each anchor has spent this tick, allowance and overflow together. */
    private final Map<Object, Integer> spent = new HashMap<>();

    /** Who asked for anything this tick, which is how many anchors the next tick plans for. */
    private final Set<Object> seen = new HashSet<>();

    private int expected = 1;

    /** How much of the budget is reserved as floors: one part in this many, so half. */
    private static final int RESERVED_FRACTION = 2;

    public ActionBudget(int total) {
        reset(total);
    }

    /**
     * Starts a new tick, sharing {@code total} out among however many anchors asked last tick.
     */
    public void reset(int total) {
        this.total = Math.max(0, total);
        // Last tick's population, because this tick's is not known until it has happened. An anchor
        // that has just started ticking is one late to be counted, and no worse off for it.
        this.expected = Math.max(1, seen.size());
        // A floor, not an even split. Splitting the whole budget evenly would reserve a share for
        // every anchor whether or not it had anything to do, so a level of mostly idle anchors
        // would cap the one anchor actually working at a fraction of a budget nobody else wanted.
        // Half is handed out as guaranteed floors and half is left in the pool, so a busy anchor
        // can still use most of the level's budget while a quiet one keeps a share it can count on.
        this.allowance = Math.max(1, this.total / (RESERVED_FRACTION * expected));
        this.overflow = Math.max(0, this.total - allowance * expected);
        spent.clear();
        seen.clear();
    }

    /**
     * Claims one action for {@code anchor}, or refuses if this tick has nothing left for it.
     */
    public boolean claim(Object anchor) {
        seen.add(anchor);
        int used = spent.getOrDefault(anchor, 0);

        // Its own allowance first, which nobody else can take.
        if (used < allowance) {
            spent.put(anchor, used + 1);
            return true;
        }
        // Then whatever the anchors that did not use theirs left behind.
        if (overflow > 0) {
            overflow--;
            spent.put(anchor, used + 1);
            return true;
        }
        return false;
    }

    /** The guaranteed share each anchor has this tick. */
    public int allowance() {
        return allowance;
    }

    /** What is still up for grabs. */
    public int overflow() {
        return overflow;
    }

    /** How many anchors this tick was shared out for. */
    public int expected() {
        return expected;
    }

    public int total() {
        return total;
    }
}
