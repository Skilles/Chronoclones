package com.skilles.chronoclones.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Why the last action was skipped, and where.
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
        NO_BLOCK("no_block", false),
        /**
         * Something is there, but not what the recording expected.
         *
         * <p>Transient by nature -- the block a routine wants may be on its way back, which is the
         * whole business of a farm -- so it says so and lets the next pass try again.
         */
        WRONG_BLOCK("wrong_block", false),
        /** On the unbreakable tag, or a block entity we refuse to touch. */
        BLACKLISTED("blacklisted", false),
        /** A protection mod or land claim cancelled the break. */
        PROTECTED("protected", false),
        /** Outside MAX_RADIUS of the anchor. */
        OUT_OF_RANGE("out_of_range", false),
        /** Target chunk is not loaded: no force-loading, by design. */
        UNLOADED("unloaded", false),
        /** Drops would not fit. Halts, so nothing is ever destroyed without being stored. */
        INVENTORY_FULL("inventory_full", true),
        NO_CHARGE("no_charge", true),
        /** The recording placed something that is not a block. */
        NOT_PLACEABLE("not_placeable", false),

        /**
         * Not enough banked experience for what the step costs, and no bottle to make it up.
         *
         * <p>Does not halt, unlike the other resources: a routine that mines ore earns its own
         * experience, and halting would stop it doing the very thing that would fix this.
         */
        NO_EXPERIENCE("no_experience", false),

        /** There is something there, but it has no menu a clone can open. */
        NO_MENU("no_menu", false),

        /** The merchant is still there and still trading, but not this. */
        NO_OFFER("no_offer", false),

        /**
         * The offer exists and is sold out. Transient by nature: a merchant restocks, so this says
         * so rather than reporting the trade as missing.
         */
        OUT_OF_STOCK("out_of_stock", false),

        /** An action told to finish something ran out of patience for it. */
        UNFINISHED("unfinished", false),
        /** No matching item in the anchor inventory to place. */
        NO_ITEM("no_item", false),
        /** Target position is occupied by something that cannot be replaced. */
        OBSTRUCTED("obstructed", false),

        /**
         * There was nothing where the routine reached.
         *
         * <p>Only that. Every interaction that declined used to report this, which told a player
         * whose furnace was lit and whose crossbow was empty exactly the same thing: that a routine
         * aimed at nothing, which was true of neither.
         */
        NO_TARGET("no_target", false),

        /** The thing is there and it said no: an interaction that returned PASS or FAIL. */
        REFUSED("refused", false),

        /**
         * The menu is open, but the square the step names cannot be reached in it.
         *
         * <p>An anchor's storage is one page per clone, and a routine recorded reaching into the
         * fourth clone's page has nowhere to put anything once the splitter making that clone is
         * pulled out. Saying so beats quietly putting somebody's ore in the wrong clone.
         */
        NO_SLOT("no_slot", false),

        /** The item is still cooling down from the last time it was used. */
        ON_COOLDOWN("on_cooldown", false),

        /** A weapon with nothing to fire, or an item with nothing left to consume. */
        NO_AMMO("no_ammo", false),

        /**
         * The item cannot be replayed at all, rather than having declined this once.
         *
         * <p>Not halting, though it is tempting: a halt has to be something the anchor can notice
         * resolving, and nothing about an item without a duration ever resolves. Halting on it
         * would be a trap with no way out, so the routine says so on each pass and carries on with
         * the rest of its work.
         */
        UNSUPPORTED("unsupported", false);

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
