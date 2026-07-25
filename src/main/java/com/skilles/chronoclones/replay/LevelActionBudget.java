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
 *
 * <p>Motion is cheap — driving a ghost is one position assignment — but world mutation is not, and
 * a base full of anchors could otherwise issue unbounded block breaks in a single tick. This bounds
 * the expensive half.
 *
 * <p>Anchors that run out of budget simply do not act this tick and retry on the next one, rather
 * than losing the action. Because {@code CloneRuntime}'s action cursor only advances when an action
 * is actually attempted, a starved anchor resumes exactly where it left off.
 *
 * <p><b>Note on the spec's staggered ticking.</b>  also suggests skipping whole anchor ticks on a
 * hashed schedule. That is not done here: the anchor's per-tick work is what keeps ghosts moving
 * smoothly, and staggering it would make them visibly stutter. The budget bounds the part that
 * actually costs, which is the point of the suggestion.
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
