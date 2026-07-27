package com.skilles.chronoclones.recording;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/** The kinds of action a recording can contain, their fidelity tier, and their charge cost. */
public enum ChronoActionType implements StringRepresentable {

    BREAK_BLOCK("break_block", 0, 10),
    PLACE_BLOCK("place_block", 1, 5),
    /** Hauling is a place-tier convenience, not an interaction: it swings nothing and hits nothing. */
    USE_CONTAINER("use_container", 1, 2),
    ATTACK_ENTITY("attack_entity", 2, 20),
    USE_ON_BLOCK("use_on_block", 3, 5),
    USE_ITEM("use_item", 3, 5),
    INTERACT_ENTITY("interact_entity", 3, 5);

    public static final Codec<ChronoActionType> CODEC = StringRepresentable.fromEnum(ChronoActionType::values);

    private final String name;
    private final int fidelityTier;
    private final int chargeCost;

    ChronoActionType(String name, int fidelityTier, int chargeCost) {
        this.name = name;
        this.fidelityTier = fidelityTier;
        this.chargeCost = chargeCost;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }

    public int fidelityTier() {
        return fidelityTier;
    }

    public int chargeCost() {
        return chargeCost;
    }

    public boolean permittedAt(int tier) {
        return fidelityTier <= tier;
    }
}
