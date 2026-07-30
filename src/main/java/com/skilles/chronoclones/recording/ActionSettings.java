package com.skilles.chronoclones.recording;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * How an action should be interpreted, over and above what the recording captured.
 *
 * <p>Every field starts at what the player did, so a routine nobody has edited behaves exactly as
 * the recording behaved. The editor is where a player opts into anything narrower.
 */
public record ActionSettings(String name, SlotRule slot, TargetRule target,
                            TransferRule transfer, List<StepSettings> steps) {

    public static final ActionSettings DEFAULT = new ActionSettings(
            "", SlotRule.DEFAULT, TargetRule.DEFAULT, TransferRule.DEFAULT, List.of());

    public ActionSettings {
        steps = List.copyOf(steps);
    }

    /** Empty for "call it whatever the action is", which is what the tooltips already do. */
    public boolean hasName() {
        return !name.isBlank();
    }

    public ActionSettings withName(String name) {
        return new ActionSettings(name, slot, target, transfer, steps);
    }

    public ActionSettings withSlot(SlotRule slot) {
        return new ActionSettings(name, slot, target, transfer, steps);
    }

    public ActionSettings withTarget(TargetRule target) {
        return new ActionSettings(name, slot, target, transfer, steps);
    }

    public ActionSettings withTransfer(TransferRule transfer) {
        return new ActionSettings(name, slot, target, transfer, steps);
    }

    /**
     * What one step of a session was told, which for an unedited step is nothing.
     *
     * <p>The list is short or empty by design: a routine nobody has opened the editor on carries no
     * step settings at all, and one edited step does not oblige its neighbours to be described.
     */
    public StepSettings step(int index) {
        return index >= 0 && index < steps.size() ? steps.get(index) : StepSettings.DEFAULT;
    }

    /** This, with one step replaced, padded with defaults up to it if need be. */
    public ActionSettings withStep(int index, StepSettings step) {
        if (index < 0) {
            return this;
        }
        List<StepSettings> next = new java.util.ArrayList<>(steps);
        while (next.size() <= index) {
            next.add(StepSettings.DEFAULT);
        }
        next.set(index, step);
        return new ActionSettings(name, slot, target, transfer, next);
    }

    /**
     * What one step of a container session was told, over and above what it recorded.
     *
     * @param enabled false to skip the step entirely, which is how one is dropped without touching
     *                the recording that would have to be performed again to get it back
     */
    public record StepSettings(String name, SlotRule slot, TransferRule transfer, boolean enabled) {

        public static final StepSettings DEFAULT =
                new StepSettings("", SlotRule.DEFAULT, TransferRule.DEFAULT, true);

        public boolean hasName() {
            return !name.isBlank();
        }

        public StepSettings withName(String name) {
            return new StepSettings(name, slot, transfer, enabled);
        }

        public StepSettings withSlot(SlotRule slot) {
            return new StepSettings(name, slot, transfer, enabled);
        }

        public StepSettings withTransfer(TransferRule transfer) {
            return new StepSettings(name, slot, transfer, enabled);
        }

        public StepSettings withEnabled(boolean enabled) {
            return new StepSettings(name, slot, transfer, enabled);
        }
    }

    /**
     * Which square an action reaches into for the item it needs.
     */
    public record SlotRule(Mode mode, int slot) {

        /** No square was recorded, so there is nothing to prefer. */
        public static final int NONE = -1;

        public static final SlotRule DEFAULT = new SlotRule(Mode.PREFER, NONE);

        public enum Mode implements StringRepresentable {
            /** The recorded square first, then anywhere: stock rarely lands back where it was. */
            PREFER("prefer"),
            /** The recorded square or nothing, for a routine that sorts as it works. */
            EXACT("exact"),
            /** Anywhere at all, ignoring what was recorded. */
            ANY("any");

            private final String name;

            Mode(String name) {
                this.name = name;
            }

            @Override
            public @NonNull String getSerializedName() {
                return name;
            }
        }

        public static SlotRule prefer(int slot) {
            return new SlotRule(Mode.PREFER, slot);
        }

        /** The square to look in first, or {@link #NONE} to start the search at the beginning. */
        public int preferred() {
            return mode == Mode.ANY ? NONE : slot;
        }

        /** True when a miss at {@link #preferred()} is a failure rather than a hint. */
        public boolean strict() {
            return mode == Mode.EXACT;
        }
    }

    /**
     * How an attack or an entity interaction chooses what to act on, and when it is finished.
     */
    public record TargetRule(List<Holder<EntityType<?>>> filter, double radius, boolean sticky,
                             Completion completion) {

        /** About a player's own reach: a mob that wandered is still found, a room is not cleared. */
        public static final double DEFAULT_RADIUS = 4.0;

        public static final TargetRule DEFAULT =
                new TargetRule(List.of(), DEFAULT_RADIUS, false, Completion.ONCE);

        public TargetRule {
            filter = List.copyOf(filter);
        }

        public enum Completion implements StringRepresentable {
            /** One swing, as the recording captured it. */
            ONCE("once"),
            /** Keep going until the target is dead, because that is what the player achieved. */
            UNTIL_DEAD("until_dead");

            private final String name;

            Completion(String name) {
                this.name = name;
            }

            @Override
            public @NonNull String getSerializedName() {
                return name;
            }
        }

        /** An empty filter defers to the action's own recorded type, then to the nearest thing. */
        public boolean accepts(EntityType<?> type) {
            if (filter.isEmpty()) {
                return true;
            }
            for (Holder<EntityType<?>> allowed : filter) {
                if (allowed.value() == type) {
                    return true;
                }
            }
            return false;
        }

        /** A setting may narrow the anchor's reach but never extend it. */
        public double radiusWithin(int maxRadius) {
            return Math.clamp(radius, 0.0, maxRadius);
        }

        public TargetRule withRadius(double radius) {
            return new TargetRule(filter, radius, sticky, completion);
        }

        public TargetRule withSticky(boolean sticky) {
            return new TargetRule(filter, radius, sticky, completion);
        }

        public TargetRule withCompletion(Completion completion) {
            return new TargetRule(filter, radius, sticky, completion);
        }

        public TargetRule withFilter(List<Holder<EntityType<?>>> filter) {
            return new TargetRule(filter, radius, sticky, completion);
        }
    }

    /**
     * What a container session is allowed to carry in and out.
     *
     * <p>The squares it may lend from come from the slot rule: an exact rule confines the session to
     * one square, and anything looser lends the whole inventory, which is what a player does.
     */
    public record TransferRule(List<Holder<Item>> items, QuantityRule quantity) {

        public static final TransferRule DEFAULT = new TransferRule(List.of(), QuantityRule.DEFAULT);

        public TransferRule {
            items = List.copyOf(items);
        }

        /** An empty list is every item, which is what the clicks alone would have moved. */
        public boolean allows(Item item) {
            if (items.isEmpty()) {
                return true;
            }
            for (Holder<Item> allowed : items) {
                if (allowed.value() == item) {
                    return true;
                }
            }
            return false;
        }

        public TransferRule withItems(List<Holder<Item>> items) {
            return new TransferRule(items, quantity);
        }

        public TransferRule withQuantity(QuantityRule quantity) {
            return new TransferRule(items, quantity);
        }
    }

    /**
     * How much a session may carry in total, across every square it lends.
     */
    public record QuantityRule(Mode mode, int count) {

        public static final QuantityRule DEFAULT = new QuantityRule(Mode.ANY, 0);

        public enum Mode implements StringRepresentable {
            /** Whatever the clone is holding, which is what a player would have had. */
            ANY("any"),
            /** A ceiling, for a routine that should feed a furnace rather than empty into it. */
            AT_MOST("at_most");

            private final String name;

            Mode(String name) {
                this.name = name;
            }

            @Override
            public @NonNull String getSerializedName() {
                return name;
            }
        }

        public static QuantityRule atMost(int count) {
            return count <= 0 ? DEFAULT : new QuantityRule(Mode.AT_MOST, count);
        }

        /** How many items may be lent before the session must make do. */
        public int budget() {
            return mode == Mode.ANY ? Integer.MAX_VALUE : Math.max(0, count);
        }
    }
}
