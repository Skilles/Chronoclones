package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

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

    public enum ItemRule implements StringRepresentable {

        SAME_ITEM("same_item"),

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

    public boolean hasName() {
        return !name.isBlank();
    }

    public ActionSettings withName(String name) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
    }

    public ActionSettings withSlot(SlotRule slot) {
        return new ActionSettings(name, slot, tool, recordedSubject, target, transfer, steps, item);
    }

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

    public StepSettings step(int index) {
        return index >= 0 && index < steps.size() ? steps.get(index) : StepSettings.DEFAULT;
    }

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

        /** Empty means follow the recording, so a re-taken recording is still followed. */
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

    public enum ToolRule implements StringRepresentable {

        EXACT("exact"),

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

    public record SlotRule(Mode mode, int slot) {

        public static final int NONE = -1;

        public static final SlotRule DEFAULT = new SlotRule(Mode.PREFER, NONE);

        public enum Mode implements StringRepresentable {

            PREFER("prefer"),
            EXACT("exact"),
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

        public int preferred() {
            return mode == Mode.ANY ? NONE : slot;
        }

        public boolean strict() {
            return mode == Mode.EXACT;
        }
    }

    public record TargetRule(List<Holder<EntityType<?>>> filter, double radius, boolean sticky,
                             Completion completion) {
        public static final double DEFAULT_RADIUS = 4.0;

        public static final TargetRule DEFAULT =
                new TargetRule(List.of(), DEFAULT_RADIUS, false, Completion.ONCE);

        public TargetRule {
            filter = List.copyOf(filter);
        }

        public enum Completion implements StringRepresentable {

            ONCE("once"),
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

        /** Finishing a target off means finishing that one, whatever the lock flag says. */
        public boolean locksTarget() {
            return sticky || completion == Completion.UNTIL_DEAD;
        }

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

    public record TransferRule(List<Holder<Item>> items, QuantityRule quantity) {

        public static final TransferRule DEFAULT = new TransferRule(List.of(), QuantityRule.DEFAULT);

        public TransferRule {
            items = List.copyOf(items);
        }

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

    public record QuantityRule(Mode mode, int count) {

        public static final QuantityRule DEFAULT = new QuantityRule(Mode.ANY, 0);

        public enum Mode implements StringRepresentable {

            ANY("any"),
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

        public int budget() {
            return mode == Mode.ANY ? Integer.MAX_VALUE : Math.max(0, count);
        }
    }
}
