package com.skilles.chronoclones.replay;

import java.util.UUID;

import com.skilles.chronoclones.block.ExperienceStore;

/** Who an anchor acts as for one action, and the experience it acts with. */
public final class Operator {

    private final UUID id;
    private final String name;
    private int experience;

    public Operator(UUID id, String name, ExperienceStore experience) {
        this.id = id;
        this.name = name;
        this.experience = experience.points();
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int experience() {
        return experience;
    }

    public ExperienceStore store() {
        return new ExperienceStore(experience);
    }

    void setExperience(int points) {
        experience = Math.max(0, points);
    }

    public void addExperience(int points) {
        setExperience(experience + points);
    }
}
