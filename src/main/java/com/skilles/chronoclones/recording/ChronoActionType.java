package com.skilles.chronoclones.recording;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/** The kinds of action a recording can contain, and what each costs an anchor to replay. */
public enum ChronoActionType implements StringRepresentable {

    BREAK_BLOCK("break_block", 10),
    PLACE_BLOCK("place_block", 5),
    /** Hauling swings nothing and hits nothing, so it is the cheapest thing a clone can do. */
    USE_CONTAINER("use_container", 2),
    ATTACK_ENTITY("attack_entity", 20),
    USE_ON_BLOCK("use_on_block", 5),
    USE_ITEM("use_item", 5),
    INTERACT_ENTITY("interact_entity", 5);

    public static final Codec<ChronoActionType> CODEC = StringRepresentable.fromEnum(ChronoActionType::values);

    private final String name;
    private final int chargeCost;

    ChronoActionType(String name, int chargeCost) {
        this.name = name;
        this.chargeCost = chargeCost;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name;
    }

    public int chargeCost() {
        return chargeCost;
    }
}
