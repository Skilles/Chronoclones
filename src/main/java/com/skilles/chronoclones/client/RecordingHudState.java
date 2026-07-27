package com.skilles.chronoclones.client;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Decides whether the recording overlay is showing, and what it says.
 */
public final class RecordingHudState {

    /** Roughly two seconds. Long enough to ride out a lag spike, short enough not to lie for long. */
    public static final long STALL_TICKS = 40;

    /**
     * How long an out-of-range warning stays up.
     */
    public static final long WARNING_TICKS = 50;

    private @Nullable UUID session;
    private int elapsedTicks;
    private int actionCount;
    private long lastAdvance;
    private long warningUntil = Long.MIN_VALUE;

    /**
     * Folds in this frame's reading of the stamp.
     *
     * @param session null when no recorder in the inventory carries a stamp
     * @param now     client game time, which stops when the world does
     * @return whether the overlay should draw
     */
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

        // A stamp that stopped moving belongs to a session that stopped existing.
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

    /** Elapsed time as {@code m:ss}, which is how long a routine reads to a person. */
    public static String clock(int ticks) {
        int seconds = ticks / 20;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    /**
     * How full a cap is, clamped to 0..1.
     */
    public static float fraction(int used, int cap) {
        if (cap <= 0) {
            return 1.0f;
        }
        return Math.clamp(used / (float) cap, 0.0f, 1.0f);
    }
}
