package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.skilles.chronoclones.recording.ActionSettings.TargetRule;

import org.jspecify.annotations.Nullable;

/**
 * Turns a run of swings into what the player was trying to do.
 *
 * <p>A recording captures five swings where the player meant "kill that". Replayed literally, five
 * swings against a tougher mob leave it alive and the clone walks away. Collapsing the run into one
 * action with a goal is the difference between repeating the input and repeating the outcome.
 */
public final class AttackIntent {

    private AttackIntent() {}

    /**
     * One recorded action, with the entity it was aimed at if it was a swing.
     */
    public record Swing(TimedAction timed, @Nullable UUID target) {

        public static Swing of(TimedAction timed) {
            return new Swing(timed, null);
        }
    }

    /**
     * Collapses adjacent swings at one target, and marks the survivor if the target died.
     *
     * <p>Adjacent only: a player who hit a zombie, turned to a cow, then came back meant three
     * things, not two.
     */
    public static List<TimedAction> coalesce(List<Swing> swings, Set<UUID> killed) {
        List<TimedAction> collapsed = new ArrayList<>(swings.size());

        for (int i = 0; i < swings.size(); i++) {
            Swing first = swings.get(i);
            if (first.target() == null) {
                collapsed.add(first.timed());
                continue;
            }

            while (i + 1 < swings.size() && first.target().equals(swings.get(i + 1).target())) {
                i++;
            }

            // The first swing's tick, so the clone starts when the player started.
            collapsed.add(killed.contains(first.target())
                    ? withCompletion(first.timed(), TargetRule.Completion.UNTIL_DEAD)
                    : first.timed());
        }
        return List.copyOf(collapsed);
    }

    private static TimedAction withCompletion(TimedAction timed, TargetRule.Completion completion) {
        ActionSettings settings = timed.settings();
        return timed.withSettings(settings.withTarget(settings.target().withCompletion(completion)));
    }
}
