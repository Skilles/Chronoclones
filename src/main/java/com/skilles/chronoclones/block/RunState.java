package com.skilles.chronoclones.block;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * Whether an anchor is working, holding, or put away.
 *
 * <p>Pausing and stopping differ in what happens to the clones: a paused anchor keeps them standing
 * where they got to and carries on from there, where a stopped one takes them away and begins again
 * from the top when it is next told to run.
 */
public enum RunState implements StringRepresentable {

    RUNNING("running"),
    PAUSED("paused"),
    STOPPED("stopped");

    private final String name;

    RunState(String name) {
        this.name = name;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }

    public boolean isRunning() {
        return this == RUNNING;
    }

    /** By ordinal, for the synced container data and the menu button that sets it. */
    public static RunState byOrdinal(int ordinal) {
        RunState[] states = values();
        return ordinal >= 0 && ordinal < states.length ? states[ordinal] : RUNNING;
    }

    public static RunState byName(String name) {
        for (RunState state : values()) {
            if (state.name.equals(name)) {
                return state;
            }
        }
        return RUNNING;
    }

    public String translationKey() {
        return "gui.chronoclones.anchor.run." + name;
    }
}
