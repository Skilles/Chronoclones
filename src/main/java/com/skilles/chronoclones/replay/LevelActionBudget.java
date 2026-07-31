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

/**
 * Caps how many clone actions the whole level may execute per tick, fairly.
 *
 * @see ActionBudget for how one tick's worth is shared out
 */
@EventBusSubscriber(modid = Chronoclones.MODID)
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

    /**
     * Claims one action for the anchor at {@code anchorPos}, or false if its share is spent.
     *
     * <p>Throttling is deliberately silent: an anchor that has used its share this tick is not
     * failing at anything, it is waiting, and the action simply runs on the next one.
     */
    public static boolean tryClaim(Level level, BlockPos anchorPos) {
        return budgetFor(level).claim(anchorPos.asLong());
    }

    private static ActionBudget budgetFor(Level level) {
        return BUDGETS.computeIfAbsent(level,
                l -> new ActionBudget(ChronoclonesConfig.MAX_ACTIONS_PER_TICK.getAsInt()));
    }
}
