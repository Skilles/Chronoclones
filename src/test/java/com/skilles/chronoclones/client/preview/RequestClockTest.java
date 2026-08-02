package com.skilles.chronoclones.client.preview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestClockTest {

    private static final long INTERVAL = 40;

    @Test
    @DisplayName("the very first request is allowed, at any world time")
    void firstRequestIsAllowed() {
        assertTrue(new RequestClock().claim(0, INTERVAL),
                "a fresh clock refused its first request: the feature would do nothing, silently");
        assertTrue(new RequestClock().claim(1, INTERVAL));
        assertTrue(new RequestClock().claim(1_000_000, INTERVAL));
    }

    @Test
    @DisplayName("a second request inside the interval is refused")
    void secondRequestIsThrottled() {
        RequestClock clock = new RequestClock();
        assertTrue(clock.claim(100, INTERVAL));

        assertFalse(clock.claim(100, INTERVAL));
        assertFalse(clock.claim(139, INTERVAL), "one tick early is still early");
    }

    @Test
    @DisplayName("the request is allowed again once the interval has passed")
    void intervalReopensTheClock() {
        RequestClock clock = new RequestClock();
        clock.claim(100, INTERVAL);

        assertTrue(clock.claim(140, INTERVAL));
        assertFalse(clock.claim(179, INTERVAL), "the interval restarts from the last request");
        assertTrue(clock.claim(180, INTERVAL));
    }

    @Test
    @DisplayName("a world clock that jumps backwards reopens it")
    void backwardsClockReopens() {
        RequestClock clock = new RequestClock();
        clock.claim(1_000_000, INTERVAL);

        assertTrue(clock.claim(500, INTERVAL),
                "a second world starts its clock over, and waiting out the first one is hours");
        assertFalse(clock.claim(501, INTERVAL), "and the throttle then runs on the new clock");
    }

    @Test
    @DisplayName("resetting reopens it immediately")
    void resetReopens() {
        RequestClock clock = new RequestClock();
        clock.claim(100, INTERVAL);
        assertFalse(clock.claim(101, INTERVAL));

        clock.reset();
        assertTrue(clock.claim(101, INTERVAL));
    }
}
