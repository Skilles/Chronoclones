package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.skilles.chronoclones.recording.ActionSettings.TargetRule;

import org.jspecify.annotations.Nullable;

/** Collapses a run of swings at one target into a single action with a goal. */
public final class AttackIntent {

    private AttackIntent() {}

    public record Swing(TimedAction timed, @Nullable UUID target) {

        public static Swing of(TimedAction timed) {
            return new Swing(timed, null);
        }
    }

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
