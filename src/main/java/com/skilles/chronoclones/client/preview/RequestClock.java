package com.skilles.chronoclones.client.preview;

/** Rate limits how often the client asks the server for a preview. */
public final class RequestClock {

    private long nextAllowed = Long.MIN_VALUE;

    public boolean claim(long now, long intervalTicks) {
        // A world clock only ever runs forward within one world, so a time before the last claim is
        // a different world, not a request too soon. Waiting for the old clock to catch up would be
        // a wait of hours.
        if (now < nextAllowed && now >= nextAllowed - intervalTicks) {
            return false;
        }
        nextAllowed = now + intervalTicks;
        return true;
    }

    public void reset() {
        nextAllowed = Long.MIN_VALUE;
    }
}
