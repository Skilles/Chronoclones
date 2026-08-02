package com.skilles.chronoclones.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedstoneStatusTest {

    @Test
    @DisplayName("an empty or stopped anchor reads zero, however it got there")
    void stoppedReadsZero() {
        assertEquals(0, RedstoneStatus.signalOf(RunState.RUNNING, false, false, false));
        assertEquals(0, RedstoneStatus.signalOf(RunState.STOPPED, true, false, false));
        assertEquals(0, RedstoneStatus.signalOf(RunState.STOPPED, true, true, true));
    }

    @Test
    @DisplayName("a paused anchor reads low")
    void pausedReadsLow() {
        assertEquals(3, RedstoneStatus.signalOf(RunState.PAUSED, true, false, false));
    }

    @Test
    @DisplayName("a stall outranks winding down: somebody should come look")
    void stalledOutranksFinishing() {
        assertEquals(7, RedstoneStatus.signalOf(RunState.RUNNING, true, true, false));
        assertEquals(7, RedstoneStatus.signalOf(RunState.RUNNING, true, true, true));
    }

    @Test
    @DisplayName("winding down reads below full so a chain can see the handoff coming")
    void finishingReadsBelowFull() {
        assertEquals(11, RedstoneStatus.signalOf(RunState.RUNNING, true, false, true));
    }

    @Test
    @DisplayName("a running anchor reads full strength")
    void runningReadsFull() {
        assertEquals(15, RedstoneStatus.signalOf(RunState.RUNNING, true, false, false));
    }
}
