package com.skilles.chronoclones.client;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

public final class RecordingHudState {

    public static final long STALL_TICKS = 40;

    public static final long WARNING_TICKS = 50;

    private @Nullable UUID session;
    private int elapsedTicks;
    private int actionCount;
    private long lastAdvance;
    private long warningUntil = Long.MIN_VALUE;

    public boolean update(@Nullable UUID session, int elapsedTicks, int actionCount,
                          boolean outOfRange, long now) {
        if (session == null) {
            this.session = null;
            return false;
        }

        if (!session.equals(this.session) || elapsedTicks != this.elapsedTicks) {
            this.session = session;
            this.lastAdvance = now;
        }
        this.elapsedTicks = elapsedTicks;
        this.actionCount = actionCount;

        if (outOfRange) {
            this.warningUntil = now + WARNING_TICKS;
        }

        if (now - this.lastAdvance >= STALL_TICKS) {
            this.session = null;
            return false;
        }
        return true;
    }

    public boolean isWarning(long now) {
        return now < warningUntil;
    }

    public int elapsedTicks() {
        return elapsedTicks;
    }

    public int actionCount() {
        return actionCount;
    }

    public static String clock(int ticks) {
        int seconds = ticks / 20;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    public static float fraction(int used, int cap) {
        if (cap <= 0) {
            return 1.0f;
        }
        return Math.clamp(used / (float) cap, 0.0f, 1.0f);
    }
}
