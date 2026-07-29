package com.skilles.chronoclones.recording;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * How an action should be interpreted, over and above what the recording captured.
 *
 * <p>Every field starts at what the player did, so a routine nobody has edited behaves exactly as
 * the recording behaved. The editor is where a player opts into anything narrower.
 */
public record ActionSettings(String name, SlotRule slot, TargetRule target) {

    public static final ActionSettings DEFAULT =
            new ActionSettings("", SlotRule.DEFAULT, TargetRule.DEFAULT);

    /** Empty for "call it whatever the action is", which is what the tooltips already do. */
    public boolean hasName() {
        return !name.isBlank();
    }

    public ActionSettings withName(String name) {
        return new ActionSettings(name, slot, target);
    }

    public ActionSettings withSlot(SlotRule slot) {
        return new ActionSettings(name, slot, target);
    }

    public ActionSettings withTarget(TargetRule target) {
        return new ActionSettings(name, slot, target);
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
}
