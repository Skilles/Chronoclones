package com.skilles.chronoclones.client.preview;

/** Rate limits how often the client asks the server for a preview. */
public final class RequestClock {

    private long nextAllowed = Long.MIN_VALUE;

    public boolean claim(long now, long intervalTicks) {
        if (now < nextAllowed) {
            return false;
        }
        nextAllowed = now + intervalTicks;
        return true;
    }

    public void reset() {
        nextAllowed = Long.MIN_VALUE;
    }
}
