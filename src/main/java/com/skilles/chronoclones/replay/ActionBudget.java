package com.skilles.chronoclones.replay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One tick of clone actions, shared out so a late-ticking anchor still gets a turn. */
public final class ActionBudget {

    private int total;

    private int allowance;

    private int overflow;

    private final Map<Object, Integer> spent = new HashMap<>();

    private final Set<Object> seen = new HashSet<>();

    private int expected = 1;

    private static final int RESERVED_FRACTION = 2;

    public ActionBudget(int total) {
        reset(total);
    }

    public void reset(int total) {
        this.total = Math.max(0, total);
        // Last tick's population: this tick's is not known until it has happened.
        this.expected = Math.max(1, seen.size());
        this.allowance = Math.max(1, this.total / (RESERVED_FRACTION * expected));
        this.overflow = Math.max(0, this.total - allowance * expected);
        spent.clear();
        seen.clear();
    }

    public boolean claim(Object anchor) {
        seen.add(anchor);
        int used = spent.getOrDefault(anchor, 0);

        if (used < allowance) {
            spent.put(anchor, used + 1);
            return true;
        }
        if (overflow > 0) {
            overflow--;
            spent.put(anchor, used + 1);
            return true;
        }
        return false;
    }

    public int allowance() {
        return allowance;
    }

    public int overflow() {
        return overflow;
    }

    public int expected() {
        return expected;
    }

    public int total() {
        return total;
    }
}
