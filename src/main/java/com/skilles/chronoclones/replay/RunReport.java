package com.skilles.chronoclones.replay;

import java.util.Arrays;
import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

/**
 * What happened to each action the last time a clone reached it. Routines loop, so entries are
 * overwritten in place: the latest attempt is the one worth diagnosing, however long ago the
 * routine was imprinted.
 */
public final class RunReport {

    public enum Outcome {
        PENDING, OK, SKIPPED, HALTED
    }

    public record Entry(Outcome outcome, FailureReason reason, int step, int cloneIndex,
                        long gameTime) {

        public static final Entry PENDING =
                new Entry(Outcome.PENDING, FailureReason.NONE, ActionResult.NO_STEP, -1, 0L);
    }

    private Entry[] entries = new Entry[0];

    public void resize(int actionCount) {
        if (entries.length == actionCount) {
            reset();
            return;
        }
        entries = new Entry[Math.max(actionCount, 0)];
        Arrays.fill(entries, Entry.PENDING);
    }

    public void reset() {
        Arrays.fill(entries, Entry.PENDING);
    }

    public void record(int action, int cloneIndex, long gameTime, ActionResult result) {
        if (action < 0 || action >= entries.length) {
            return;
        }
        Outcome outcome = result.succeeded() ? Outcome.OK
                : result.reason().halts() ? Outcome.HALTED
                : Outcome.SKIPPED;
        entries[action] = new Entry(outcome, result.reason(), result.step(), cloneIndex, gameTime);
    }

    public Entry entry(int action) {
        return action >= 0 && action < entries.length ? entries[action] : Entry.PENDING;
    }

    public int size() {
        return entries.length;
    }

    public List<Entry> snapshot() {
        return List.of(entries);
    }

    public int count(Outcome outcome) {
        int total = 0;
        for (Entry entry : entries) {
            if (entry.outcome() == outcome) {
                total++;
            }
        }
        return total;
    }
}
