package com.skilles.chronoclones.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phase-offset distribution is the visual showpiece: several clones strung out along
 * one route like a bucket brigade. It only reads that way if the offsets are genuinely even, so the
 * maths gets asserted rather than eyeballed.
 */
class CloneRuntimeTest {

    @Test
    @DisplayName("a single clone starts at the beginning of the routine")
    void singleChronoStartsAtZero() {
        assertEquals(0, CloneRuntime.phaseOffsetFor(0, 1, 200));
    }

    @Test
    @DisplayName("clones are evenly distributed along the timeline")
    void offsetsAreEvenlyDistributed() {
        assertEquals(0, CloneRuntime.phaseOffsetFor(0, 4, 200));
        assertEquals(50, CloneRuntime.phaseOffsetFor(1, 4, 200));
        assertEquals(100, CloneRuntime.phaseOffsetFor(2, 4, 200));
        assertEquals(150, CloneRuntime.phaseOffsetFor(3, 4, 200));
    }

    @Test
    @DisplayName("offsets stay in range and strictly increase for any clone count")
    void offsetsAreOrderedAndInRange() {
        int length = 600;
        for (int count = 1; count <= 16; count++) {
            int previous = -1;
            for (int i = 0; i < count; i++) {
                int offset = CloneRuntime.phaseOffsetFor(i, count, length);
                assertTrue(offset >= 0 && offset < length,
                        "offset " + offset + " out of range for count " + count);
                assertTrue(offset > previous,
                        "offsets not strictly increasing at count " + count + " index " + i);
                previous = offset;
            }
        }
    }

    @Test
    @DisplayName("offset maths does not overflow on long recordings with many clones")
    void noOverflowOnLongRoutines() {
        int offset = CloneRuntime.phaseOffsetFor(15, 16, Integer.MAX_VALUE);
        assertTrue(offset > 0, "overflowed to " + offset);
        assertTrue(offset < Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("a runtime begins at its phase offset, not at zero")
    void runtimeStartsAtItsOffset() {
        CloneRuntime runtime = new CloneRuntime(50);
        assertEquals(50, runtime.playhead());
        assertEquals(50, runtime.phaseOffset());
    }

    @Test
    @DisplayName("looping wraps the playhead and rewinds the action cursor together")
    void loopResetsPlayheadAndCursor() {
        CloneRuntime runtime = new CloneRuntime(0);
        runtime.advance(100);
        runtime.consumeAction();
        runtime.consumeAction();
        assertEquals(2, runtime.actionCursor());

        runtime.loop(100);

        assertEquals(0, runtime.playhead());
        assertEquals(0, runtime.actionCursor(),
                "cursor must rewind with the playhead or actions are skipped after the first loop");
    }

    @Test
    @DisplayName("looping preserves overshoot rather than snapping to zero")
    void loopPreservesOvershoot() {
        CloneRuntime runtime = new CloneRuntime(0);
        runtime.advance(103);
        runtime.loop(100);
        assertEquals(3, runtime.playhead());
    }

    @Test
    @DisplayName("an offset clone loops back into range and stays there over many cycles")
    void offsetChronoLoopsCleanly() {
        int length = 120;
        CloneRuntime runtime = new CloneRuntime(CloneRuntime.phaseOffsetFor(2, 3, length));

        List<Integer> visited = new ArrayList<>();
        for (int tick = 0; tick < length * 20; tick++) {
            runtime.advance(1);
            if (runtime.playhead() >= length) {
                runtime.loop(length);
            }
            visited.add(runtime.playhead());
        }

        assertTrue(visited.stream().allMatch(p -> p >= 0 && p < length),
                "playhead left the routine range");
    }

    @Test
    @DisplayName("a zero-length recording cannot wedge the loop")
    void zeroLengthIsSafe() {
        CloneRuntime runtime = new CloneRuntime(0);
        runtime.advance(5);
        runtime.loop(0);
        assertEquals(0, runtime.playhead());
        assertEquals(0, runtime.actionCursor());
    }
}
