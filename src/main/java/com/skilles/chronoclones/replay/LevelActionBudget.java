package com.skilles.chronoclones.replay;

import java.util.Map;
import java.util.WeakHashMap;

import com.skilles.chronoclones.ChronoclonesConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The per-level action budget, refilled each tick by the loader's level-tick bridge. */
public final class LevelActionBudget {

    private static final Map<Level, ActionBudget> BUDGETS = new WeakHashMap<>();

    private LevelActionBudget() {}

    /** Called at the start of every server level tick. */
    public static void resetBudget(Level level) {
        budgetFor(level).reset(ChronoclonesConfig.maxActionsPerTick());
    }

    public static boolean tryClaim(Level level, BlockPos anchorPos) {
        return budgetFor(level).claim(anchorPos.asLong());
    }

    private static ActionBudget budgetFor(Level level) {
        return BUDGETS.computeIfAbsent(level,
                l -> new ActionBudget(ChronoclonesConfig.maxActionsPerTick()));
    }
}
