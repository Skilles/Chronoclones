package com.skilles.chronoclones.replay;

import java.util.UUID;

import com.skilles.chronoclones.block.ExperienceStore;

/**
 * Who an anchor acts as for the length of one action, and the experience it acts with.
 *
 * <p>Deliberately mutable, and deliberately short-lived: one is made per action, handed to the
 * executor, and read back afterwards. That is what lets every experience source and sink work
 * without being enumerated — the fake player earns or spends in the ordinary way and the change is
 * swept back here by {@link AnchorFakePlayer#release}, the same shape as {@link HeldItemLoan}.
 */
public final class Operator {

    private final UUID id;
    private final String name;
    private int experience;

    public Operator(UUID id, String name, ExperienceStore experience) {
        this.id = id;
        this.name = name;
        this.experience = experience.points();
    }

    /** The imprinting player: the identity every event and permission check will see. */
    public UUID id() {
        return id;
    }

    /** Their name, for readable logs and protection-mod messages. */
    public String name() {
        return name;
    }

    public int experience() {
        return experience;
    }

    /** What the clone should be left holding once the action is over. */
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
