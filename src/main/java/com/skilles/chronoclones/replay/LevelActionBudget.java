package com.skilles.chronoclones.replay;

import java.util.Map;
import java.util.WeakHashMap;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Chronoclones.MODID)
/** The per-level action budget, refilled each tick. */
public final class LevelActionBudget {

    private static final Map<Level, ActionBudget> BUDGETS = new WeakHashMap<>();

    private LevelActionBudget() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        budgetFor(level).reset(ChronoclonesConfig.MAX_ACTIONS_PER_TICK.getAsInt());
    }

    public static boolean tryClaim(Level level, BlockPos anchorPos) {
        return budgetFor(level).claim(anchorPos.asLong());
    }

    private static ActionBudget budgetFor(Level level) {
        return BUDGETS.computeIfAbsent(level,
                l -> new ActionBudget(ChronoclonesConfig.MAX_ACTIONS_PER_TICK.getAsInt()));
    }
}
