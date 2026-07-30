package com.skilles.chronoclones.recording;

import java.util.stream.IntStream;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * One thing a player did inside an open menu.
 *
 * <p>A session used to be a list of raw clicks, which say what the mouse did but not what the player
 * meant by it, so the editor had nothing to offer but the session entire.
 */
public sealed interface SessionStep {

    Kind kind();

    /** The step kinds, for dispatch. */
    enum Kind implements StringRepresentable {
        MOVE("move"),
        RAW_CLICK("raw_click"),
        BUTTON("button"),
        TRADE("trade"),
        RENAME("rename");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    /**
     * Every square this step names, for the highlight drawn over an open menu.
     */
    default IntStream squares() {
        return switch (this) {
            case Move m -> m.quick() ? IntStream.of(m.from()) : IntStream.of(m.from(), m.to());
            case RawClick c -> IntStream.of(c.slot());
            // These name no square: they are the menu's own controls, not its slots.
            case Button ignored -> IntStream.empty();
            case Trade ignored -> IntStream.empty();
            case Rename ignored -> IntStream.empty();
        };
    }

    /**
     * Moving one item out of one square and into another.
     *
     * @param to       where it went, or {@link Move#ELSEWHERE} for a shift-click, whose destination
     *                 the menu chooses
     * @param observed how much of the square the click took, rather than a count, which would bake
     *                 in what the chest happened to hold that day
     */
    record Move(int from, int to, Holder<Item> item, Amount observed) implements SessionStep {

        /** A shift-click names no destination: the menu decided then and decides again now. */
        public static final int ELSEWHERE = -1;

        public boolean quick() {
            return to == ELSEWHERE;
        }

        @Override
        public Kind kind() {
            return Kind.MOVE;
        }
    }

    /** How much of a square one move took. */
    enum Amount implements StringRepresentable {
        ALL("all"),
        HALF("half"),
        ONE("one");

        public static final Codec<Amount> CODEC = StringRepresentable.fromEnum(Amount::values);

        private final String name;

        Amount(String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    /**
     * A click whose effect could not be named: a drag, a swap, a throw, a menu whose slots move
     * items around by their own rules.
     */
    record RawClick(int slot, int button, ContainerInput input) implements SessionStep {
        @Override
        public Kind kind() {
            return Kind.RAW_CLICK;
        }
    }

    /**
     * A control in the menu rather than a slot: an enchantment tier, a loom pattern, a beacon's
     * confirmation. It travels by its own packet, so there is no click to read it from.
     */
    record Button(int id) implements SessionStep {
        @Override
        public Kind kind() {
            return Kind.BUTTON;
        }
    }

    /**
     * Choosing one of a merchant's offers, by what it offers rather than where it sat in the list.
     *
     * <p>A villager's trades reorder as it levels up, and the fifth trade of that day is not the same
     * promise as the fifth trade today.
     */
    record Trade(ItemStack costA, ItemStack costB, ItemStack result) implements SessionStep {

        public Trade {
            costA = costA.copy();
            costB = costB.copy();
            result = result.copy();
        }

        /**
         * Whether this is the same offer as another.
         *
         * <p>Not {@code equals}: a record compares its fields with {@code equals}, and an
         * {@link ItemStack} does not have one -- two stacks of the same thing are only ever equal to
         * themselves. So a record of three of them is never equal to anything but itself, which is
         * how two clicks on one offer stayed two steps.
         */
        public boolean sameOffer(Trade other) {
            return ItemStack.matches(costA, other.costA)
                    && ItemStack.matches(costB, other.costB)
                    && ItemStack.matches(result, other.result);
        }

        @Override
        public Kind kind() {
            return Kind.TRADE;
        }
    }

    /** Typing a name into an anvil, which is not a click either. */
    record Rename(String text) implements SessionStep {
        @Override
        public Kind kind() {
            return Kind.RENAME;
        }
    }
}
