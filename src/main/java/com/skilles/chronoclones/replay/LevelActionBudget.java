package com.skilles.chronoclones.replay;

import java.util.Map;
import java.util.WeakHashMap;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Caps how many clone actions the whole level may execute per tick.
 */
@EventBusSubscriber(modid = Chronoclones.MODID)
public final class LevelActionBudget {

    private static final Map<Level, int[]> REMAINING = new WeakHashMap<>();

    private LevelActionBudget() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        REMAINING.computeIfAbsent(level, l -> new int[1])[0] =
                ChronoclonesConfig.MAX_ACTIONS_PER_TICK.getAsInt();
    }

    /** Claims one action for this tick, or false if the level's budget is spent. */
    public static boolean tryClaim(Level level) {
        int[] remaining = REMAINING.get(level);
        if (remaining == null) {
            // No tick has run yet for this level; allow rather than deadlock.
            return true;
        }
        if (remaining[0] <= 0) {
            return false;
        }
        remaining[0]--;
        return true;
    }
}
