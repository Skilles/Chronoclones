package com.skilles.chronoclones.client.preview;

/**
 * Rate-limits a repeating request against the world clock.
 *
 * <p>Its own class for one reason: the obvious way to write this is wrong.
 *
 * <pre>{@code
 * private long last = Long.MIN_VALUE;
 * if (now - last >= INTERVAL) { ... }   // never fires
 * }</pre>
 *
 * <p>{@code now - Long.MIN_VALUE} overflows to a large negative number, so the comparison is false
 * forever and the first request is never sent. It fails silently and completely — the feature simply
 * does nothing, with no error to follow — and it had shipped in two caches before anyone noticed,
 * because the one preview path that worked was the one that returns before reaching it.
 *
 * <p>Storing the <em>next</em> permitted tick instead removes the subtraction, and with it the only
 * arithmetic that could overflow.
 */
public final class RequestClock {

    private long nextAllowed = Long.MIN_VALUE;

    /** Whether a request may go now; records the time if so. */
    public boolean claim(long now, long intervalTicks) {
        if (now < nextAllowed) {
            return false;
        }
        nextAllowed = now + intervalTicks;
        return true;
    }

    /** Forgets the cooldown, so the next call is allowed immediately. */
    public void reset() {
        nextAllowed = Long.MIN_VALUE;
    }
}
