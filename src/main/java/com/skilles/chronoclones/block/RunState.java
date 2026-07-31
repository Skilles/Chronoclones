package com.skilles.chronoclones.block;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

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
