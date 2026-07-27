package com.skilles.chronoclones.client.preview;

/**
 * Rate-limits a repeating request against the world clock.
 *
 * <p>Stores the next permitted tick rather than the last, because {@code now - last} with
 * {@code last} at {@code Long.MIN_VALUE} overflows and never fires.
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
