package com.skilles.chronoclones.client;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the recording overlay is on screen.
 */
class RecordingHudStateTest {

    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000cafe");

    @Test
    @DisplayName("an advancing stamp keeps the overlay up")
    void advancingStampShows() {
        RecordingHudState state = new RecordingHudState();

        for (int tick = 0; tick < 200; tick++) {
            assertTrue(state.update(SESSION, tick, tick / 10, false, tick),
                    "overlay went down at tick " + tick + " of a running session");
        }
        assertEquals(199, state.elapsedTicks());
        assertEquals(19, state.actionCount());
    }

    @Test
    @DisplayName("no stamp at all means no overlay")
    void noStampHides() {
        RecordingHudState state = new RecordingHudState();

        assertTrue(state.update(SESSION, 10, 1, false, 10));
        assertFalse(state.update(null, 0, 0, false, 11));
    }

    @Test
    @DisplayName("a stamp that stops advancing is a session that stopped existing")
    void strandedStampAgesOut() {
        RecordingHudState state = new RecordingHudState();
        assertTrue(state.update(SESSION, 40, 3, false, 100));

        // The item still says 40 ticks, and nothing will ever say otherwise.
        long stalled = 100 + RecordingHudState.STALL_TICKS - 1;
        assertTrue(state.update(SESSION, 40, 3, false, stalled),
                "gave up on a session that is only lagging");
        assertFalse(state.update(SESSION, 40, 3, false, stalled + 1),
                "a stranded stamp would leave the overlay on screen forever");
    }

    @Test
    @DisplayName("a fresh session revives the overlay after an aged-out one")
    void newSessionRevives() {
        RecordingHudState state = new RecordingHudState();
        state.update(SESSION, 40, 3, false, 100);
        assertFalse(state.update(SESSION, 40, 3, false, 100 + RecordingHudState.STALL_TICKS));

        assertTrue(state.update(OTHER, 1, 0, false, 200), "a new recording must show");
    }

    @Test
    @DisplayName("a session that restarts at the same elapsed count still counts as advancing")
    void sameElapsedDifferentSession() {
        RecordingHudState state = new RecordingHudState();
        state.update(SESSION, 5, 0, false, 0);

        // Different session, identical counters: without the id check this reads as "not advancing"
        // and a brand new recording starts its life already halfway to being aged out.
        assertTrue(state.update(OTHER, 5, 0, false, RecordingHudState.STALL_TICKS + 1));
    }

    @Test
    @DisplayName("a one-tick out-of-range flag stays readable")
    void warningIsLatched() {
        RecordingHudState state = new RecordingHudState();
        state.update(SESSION, 10, 1, true, 100);

        assertTrue(state.isWarning(100));
        // The server sets this for a single tick, which is at best one frame.
        assertTrue(state.isWarning(100 + RecordingHudState.WARNING_TICKS - 1),
                "the warning vanished before anyone could read it");
        assertFalse(state.isWarning(100 + RecordingHudState.WARNING_TICKS));
    }

    @Test
    @DisplayName("elapsed ticks read as minutes and seconds")
    void clockFormatting() {
        assertEquals("0:00", RecordingHudState.clock(0));
        assertEquals("0:09", RecordingHudState.clock(199));
        assertEquals("1:00", RecordingHudState.clock(1200));
        assertEquals("2:05", RecordingHudState.clock(2500));
    }

    @Test
    @DisplayName("cap meters stay in range, including on a misconfigured cap")
    void meterFractions() {
        assertEquals(0.0f, RecordingHudState.fraction(0, 600));
        assertEquals(0.5f, RecordingHudState.fraction(300, 600));
        assertEquals(1.0f, RecordingHudState.fraction(900, 600), "a meter must not overflow its bar");
        assertEquals(1.0f, RecordingHudState.fraction(1, 0), "a zero cap has no room, not infinite room");
    }
}
