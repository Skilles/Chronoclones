package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Optional;

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
public record ActionSettings(String name, SlotRule slot, ToolRule tool, boolean recordedSubject,
                            TargetRule target, TransferRule transfer, List<StepSettings> steps,
                            ItemRule item) {

    public static final ActionSettings DEFAULT = new ActionSettings(
            "", SlotRule.DEFAULT, ToolRule.EXACT, true, TargetRule.DEFAULT, TransferRule.DEFAULT,
            List.of(), ItemRule.SAME_ITEM);

    public ActionSettings(String name, SlotRule slot, ToolRule tool, boolean recordedSubject,
                          TargetRule target, TransferRule transfer, List<StepSettings> steps) {
        this(name, slot, tool, recordedSubject, target, transfer, steps, ItemRule.SAME_ITEM);
    }

    /**
     * How closely the item an action reaches for has to match the one that was recorded.
     *
     * <p>A recording keeps the whole item now, components included, which is the only way to tell a
     * healing potion from a harming one or a charged crossbow from an empty one. Insisting on all
     * of it by default would be worse than useless, though: a recorded tool carries its damage, so
     * a routine would stop the moment its pickaxe took a scratch.
     */
    public enum ItemRule implements StringRepresentable {
        /** Anything of the same kind, which is how a routine has always matched. */
        SAME_ITEM("same_item"),

        /**
         * The same kind carrying the same components.
         *
         * <p>For the routines where what is inside the item is the point: which potion, which
         * firework, which of a modded item's modes.
         */
        EXACT("exact");

        private final String name;

        ItemRule(String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    public ActionSettings withItem(ItemRule item) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
    }

    public ActionSettings {
        steps = List.copyOf(steps);
    }

    /** Empty for "call it whatever the action is", which is what the tooltips already do. */
    public boolean hasName() {
        return !name.isBlank();
    }

    public ActionSettings withName(String name) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
    }

    public ActionSettings withSlot(SlotRule slot) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
    }

    /**
     * Whether this acts only on the thing it recorded, or on whatever is there instead.
     *
     * <p>The block half of what {@link TargetRule#filter} is for creatures: an attack narrowed to
     * cows and a break narrowed to cobblestone are one question asked of two kinds of action, and
     * the row names itself after the answer -- "Break Cobblestone" against "Break block".
     */
    public ActionSettings withRecordedSubject(boolean recordedSubject) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
    }

    public ActionSettings withTool(ToolRule tool) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
    }

    public ActionSettings withTarget(TargetRule target) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
    }

    public ActionSettings withTransfer(TransferRule transfer) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
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
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, next, item);
    }

    /**
     * What one step of a container session was told, over and above what it recorded.
     *
     * @param enabled false to skip the step entirely, which is how one is dropped without touching
     *                the recording that would have to be performed again to get it back
     */
    public record StepSettings(String name, SlotRule slot, List<Holder<Item>> items, boolean enabled,
                               Optional<SessionStep.Amount> amount) {

        public static final StepSettings DEFAULT =
                new StepSettings("", SlotRule.DEFAULT, List.of(), true, Optional.empty());

        public StepSettings {
            items = List.copyOf(items);
        }

        public boolean hasName() {
            return !name.isBlank();
        }

        /**
         * An empty list is anything, which is what the step alone would have moved.
         *
         * <p>An item list rather than a whole {@link TransferRule}: how much a move takes is its
         * {@link #amount}, and offering a count as well would be two controls for one question.
         */
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

        /**
         * How much to move, which unless somebody has said otherwise is how much was moved.
         *
         * <p>Empty rather than a copy of the recorded amount: a step nobody has edited should read
         * as the recording, including after the recording is re-taken.
         */
        public SessionStep.Amount amountOr(SessionStep.Amount observed) {
            return amount.orElse(observed);
        }

        public StepSettings withName(String name) {
            return new StepSettings(name, slot, items, enabled, amount);
        }

        public StepSettings withSlot(SlotRule slot) {
            return new StepSettings(name, slot, items, enabled, amount);
        }

        public StepSettings withItems(List<Holder<Item>> items) {
            return new StepSettings(name, slot, items, enabled, amount);
        }

        public StepSettings withEnabled(boolean enabled) {
            return new StepSettings(name, slot, items, enabled, amount);
        }

        public StepSettings withAmount(Optional<SessionStep.Amount> amount) {
            return new StepSettings(name, slot, items, enabled, amount);
        }
    }

    /**
     * How a break chooses what to swing.
     *
     * <p>An enum rather than a flag because the question has more than two honest answers: picking
     * by tier, or by what the drops need rather than by speed, are both things somebody will want.
     */
    public enum ToolRule implements StringRepresentable {
        /**
         * Another of the very tool that was recorded.
         *
         * <p>By kind, not by object: a routine recorded with a stone pickaxe will swing any stone
         * pickaxe in the anchor, and will not settle for an iron one.
         */
        EXACT("exact"),

        /**
         * Whatever in the clone's own squares breaks this block best, bare hands included.
         *
         * <p>Hands only where they would still drop something: a routine that pulverises stone into
         * nothing is worse than one that stops and says it has no pickaxe.
         */
        SMART("smart");

        private final String name;

        ToolRule(String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
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

        /**
         * Whether this action stays on the creature it chose.
         *
         * <p>Finishing something off means finishing <em>that</em> one. Picking afresh every swing
         * with an until-dead action was a contradiction the editor let you build: the wording
         * promised to keep going until the target was dead while the selection wandered between
         * whatever was nearest, so a pen of cows all ended up half-hurt and none of them died.
         *
         * <p>Asked here rather than checked at each call site, so a recording that arrives from
         * somewhere else cannot express the contradiction either.
         */
        public boolean locksTarget() {
            return sticky || completion == Completion.UNTIL_DEAD;
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
