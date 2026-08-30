package com.skilles.chronoclones.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Why the last action was skipped, and where. */
public record DiagnosticState(FailureReason reason, BlockPos localPos, int tick) {

    public static final DiagnosticState NONE = new DiagnosticState(FailureReason.NONE, BlockPos.ZERO, 0);

    public static final Codec<DiagnosticState> CODEC = RecordCodecBuilder.create(i -> i.group(
            FailureReason.CODEC.fieldOf("reason").forGetter(DiagnosticState::reason),
            BlockPos.CODEC.fieldOf("pos").forGetter(DiagnosticState::localPos),
            Codec.INT.fieldOf("tick").forGetter(DiagnosticState::tick)
    ).apply(i, DiagnosticState::new));

    public boolean isFailure() {
        return reason != FailureReason.NONE;
    }

    public boolean halts() {
        return reason.halts();
    }

    public enum FailureReason implements StringRepresentable {

        NONE("none", false),
        NO_BLOCK("no_block", false),
        WRONG_BLOCK("wrong_block", false),
        BLACKLISTED("blacklisted", false),
        PROTECTED("protected", false),
        OUT_OF_RANGE("out_of_range", false),
        UNLOADED("unloaded", false),
        INVENTORY_FULL("inventory_full", true),
        NO_CHARGE("no_charge", true),
        NOT_PLACEABLE("not_placeable", false),

        NO_EXPERIENCE("no_experience", false),

        NO_MENU("no_menu", false),

        NO_OFFER("no_offer", false),

        OUT_OF_STOCK("out_of_stock", false),

        UNFINISHED("unfinished", false),
        NO_ITEM("no_item", false),
        OBSTRUCTED("obstructed", false),

        NO_TARGET("no_target", false),

        REFUSED("refused", false),

        NO_SLOT("no_slot", false),

        ON_COOLDOWN("on_cooldown", false),

        NO_AMMO("no_ammo", false),

        UNSUPPORTED("unsupported", false),

        // Appended after the fact: the menu syncs this enum by ordinal.
        NO_TOOL("no_tool", false),

        NO_WEAPON("no_weapon", false);

        public static final Codec<FailureReason> CODEC = StringRepresentable.fromEnum(FailureReason::values);

        private final String name;
        private final boolean halts;

        FailureReason(String name, boolean halts) {
            this.name = name;
            this.halts = halts;
        }

        @Override
        @NonNull
        public String getSerializedName() {
            return name;
        }

        public boolean halts() {
            return halts;
        }

        public String translationKey() {
            return "diagnostic.chronoclones." + name;
        }
    }

    public static DiagnosticState of(FailureReason reason, @Nullable BlockPos localPos, int tick) {
        return new DiagnosticState(reason, localPos == null ? BlockPos.ZERO : localPos, tick);
    }

    public static boolean canResume(FailureReason reason, boolean hasCharge, boolean hasInventoryRoom) {
        return switch (reason) {
            case NO_CHARGE -> hasCharge;
            case INVENTORY_FULL -> hasInventoryRoom;
            default -> !reason.halts();
        };
    }
}
