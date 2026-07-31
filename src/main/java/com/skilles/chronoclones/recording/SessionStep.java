package com.skilles.chronoclones.recording;

import java.util.stream.IntStream;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/** One step of a container session. */
public sealed interface SessionStep {

    Kind kind();

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

    default IntStream squares() {
        return switch (this) {
            case Move m -> m.quick() ? IntStream.of(m.from()) : IntStream.of(m.from(), m.to());
            case RawClick c -> IntStream.of(c.slot());
            case Button ignored -> IntStream.empty();
            case Trade ignored -> IntStream.empty();
            case Rename ignored -> IntStream.empty();
        };
    }

    record Move(int from, int to, Holder<Item> item, Amount observed) implements SessionStep {

        public static final int ELSEWHERE = -1;

        public boolean quick() {
            return to == ELSEWHERE;
        }

        @Override
        public Kind kind() {
            return Kind.MOVE;
        }
    }

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

    record RawClick(int slot, int button, ContainerInput input) implements SessionStep {

        @Override
        public Kind kind() {
            return Kind.RAW_CLICK;
        }
    }

    record Button(int id) implements SessionStep {

        @Override
        public Kind kind() {
            return Kind.BUTTON;
        }
    }

    record Trade(ItemStack costA, ItemStack costB, ItemStack result) implements SessionStep {

        public Trade {
            costA = costA.copy();
            costB = costB.copy();
            result = result.copy();
        }

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

    record Rename(String text) implements SessionStep {

        @Override
        public Kind kind() {
            return Kind.RENAME;
        }
    }
}
