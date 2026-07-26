package com.skilles.chronoclones.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Why the last action was skipped, and where.
 *
 * <p>The spec is emphatic that this is not optional: divergence between the recording and the world
 * is the most confusing part of the mod, so every skip has to be legible rather than silent. A
 * halted anchor that cannot say why is indistinguishable from a broken one.
 */
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

    /** Whether this reason stops the anchor rather than skipping a single action. */
    public boolean halts() {
        return reason.halts();
    }

    public enum FailureReason implements StringRepresentable {
        NONE("none", false),
        /** Nothing there to break. */
        NO_BLOCK("no_block", false),
        /** Something is there, but not what the recording expected. */
        WRONG_BLOCK("wrong_block", false),
        /** On the unbreakable tag, or a block entity we refuse to touch. */
        BLACKLISTED("blacklisted", false),
        /** A protection mod or land claim cancelled the break. */
        PROTECTED("protected", false),
        /** Outside MAX_RADIUS of the anchor. */
        OUT_OF_RANGE("out_of_range", false),
        /** Target chunk is not loaded — no force-loading, by design. */
        UNLOADED("unloaded", false),
        /** Drops would not fit. Halts, so nothing is ever destroyed without being stored. */
        INVENTORY_FULL("inventory_full", true),
        /** Charge buffer empty. */
        NO_CHARGE("no_charge", true),
        /** Action type not permitted at the anchor's fidelity tier. */
        NOT_PERMITTED("not_permitted", false),
        /** No matching item in the anchor inventory to place. */
        NO_ITEM("no_item", false),
        /** Target position is occupied by something that cannot be replaced. */
        OBSTRUCTED("obstructed", false),
        /** Nothing to attack within reach. */
        NO_TARGET("no_target", false);

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

    /**
     * Whether a halted anchor may resume, given the conditions that caused the halt.
     *
     * <p>A halt has to be able to clear itself once the player fixes the cause. Both halting
     * reasons describe a resource state, not damage: charge comes back when fuel is added, and
     * space comes back when the inventory is emptied. Requiring a re-imprint to clear either would
     * make the halt a trap rather than a pause.
     *
     * <p>Pure so the recovery rule can be asserted without a running level.
     */
    public static boolean canResume(FailureReason reason, boolean hasCharge, boolean hasInventoryRoom) {
        return switch (reason) {
            case NO_CHARGE -> hasCharge;
            case INVENTORY_FULL -> hasInventoryRoom;
            // Everything else either does not halt, or is not something the anchor can detect
            // resolving on its own.
            default -> !reason.halts();
        };
    }
}
