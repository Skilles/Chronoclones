package com.skilles.chronoclones.client;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Decides whether the recording overlay is showing, and what it says.
 *
 * <p>Kept apart from the drawing, and free of any client type, because the interesting part is not
 * the rectangles — it is knowing when to stop. The overlay reads a {@code RecordingProgress} stamp
 * off an item in the inventory, and a stamp is a fact about an item rather than about a live
 * session: the recorder's own code has a "lost session" path that logs a warning precisely because a
 * stamp can be left behind. An overlay that trusted the stamp would sit on screen forever.
 *
 * <p>So the rule is not "a stamp exists" but "a stamp is advancing". The elapsed counter is rewritten
 * every server tick; if it stops moving for {@link #STALL_TICKS} the session is gone, whatever the
 * item still says.
 */
public final class RecordingHudState {

    /** Roughly two seconds. Long enough to ride out a lag spike, short enough not to lie for long. */
    public static final long STALL_TICKS = 40;

    /**
     * How long an out-of-range warning stays up.
     *
     * <p>The server sets that flag for a single tick and clears it. One tick is one frame at best,
     * which is no warning at all — and this is the failure a player most needs to see, because the
     * action they just performed was silently not recorded.
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
     * @param now     the client's game time, which stops when the world does — so a paused single
     *                player game does not age the session out from under itself
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
     *
     * <p>Zero or negative caps come from a misconfigured server rather than from anything the player
     * did; reporting them as full is the reading that does not divide by zero and does not claim
     * there is room.
     */
    public static float fraction(int used, int cap) {
        if (cap <= 0) {
            return 1.0f;
        }
        return Math.clamp(used / (float) cap, 0.0f, 1.0f);
    }
}
