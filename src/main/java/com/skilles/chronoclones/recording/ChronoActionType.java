package com.skilles.chronoclones.recording;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The kinds of action a recording can contain, the fidelity ladder they are gated behind
 * ({@code BREAK -> +PLACE -> +ATTACK -> +USE}), and their charge costs.
 *
 * <p>Serialised by name rather than ordinal so reordering this enum cannot silently reinterpret
 * saved recordings as a different action.
 *
 * <p>Deliberately free of registry access: codecs live in {@link RecordingCodecs} so that this
 * enum and the data records stay loadable — and therefore unit testable — without bootstrapping
 * the game.
 */
public enum ChronoActionType implements StringRepresentable {

    BREAK_BLOCK("break_block", 0, 10),
    PLACE_BLOCK("place_block", 1, 5),
    /** Hauling is a place-tier convenience, not an interaction — it swings nothing and hits nothing. */
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
    public String getSerializedName() {
        return name;
    }

    /** Minimum fidelity upgrade tier that permits this action. */
    public int fidelityTier() {
        return fidelityTier;
    }

    public int chargeCost() {
        return chargeCost;
    }

    /** True if an anchor upgraded to {@code tier} is allowed to execute this action type. */
    public boolean permittedAt(int tier) {
        return fidelityTier <= tier;
    }
}
