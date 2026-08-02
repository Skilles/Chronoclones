package com.skilles.chronoclones.client.preview;

/**
 * Which replies may speak for the origin. A nudge moves the preview at once, but a request
 * already on the wire answers with the world as it was before the nudge landed - and taking
 * that answer flashes the preview back to where it started.
 */
public final class NudgeLedger {

    private int nudges;
    private int nudgesWhenAsked;

    public void asked() {
        nudgesWhenAsked = nudges;
    }

    public void nudged() {
        nudges++;
    }

    public boolean replyKnowsTheOrigin() {
        return nudgesWhenAsked == nudges;
    }
}
