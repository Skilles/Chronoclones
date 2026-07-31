package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.phys.Vec3;

public record Recording(
        List<MotionSample> motion,
        List<TimedAction> actions,
        int lengthTicks,
        String authorName,
        UUID authorId,
        boolean creative) {
    public Recording {
        motion = List.copyOf(motion);
        actions = List.copyOf(actions);
    }

    public Recording(List<MotionSample> motion, List<TimedAction> actions, int lengthTicks,
                     String authorName, UUID authorId) {
        this(motion, actions, lengthTicks, authorName, authorId, false);
    }

    public boolean isEmpty() {
        return motion.isEmpty() && actions.isEmpty();
    }

    public Recording withSettings(int index, ActionSettings settings) {
        List<TimedAction> edited = new java.util.ArrayList<>(actions);
        edited.set(index, edited.get(index).withSettings(settings));
        return new Recording(motion, edited, lengthTicks, authorName, authorId, creative);
    }

    public Recording without(int index) {
        List<TimedAction> kept = new java.util.ArrayList<>(actions);
        kept.remove(index);
        return new Recording(motion, kept, lengthTicks, authorName, authorId, creative);
    }

    public int lengthSeconds() {
        return lengthTicks / 20;
    }

    public Map<ChronoActionType, Integer> actionCounts() {
        Map<ChronoActionType, Integer> counts = new java.util.EnumMap<>(ChronoActionType.class);
        for (TimedAction timed : actions) {
            counts.merge(timed.action().type(), 1, Integer::sum);
        }
        return counts;
    }

    public double reach() {
        double maxSqr = 0.0;
        for (MotionSample sample : motion) {
            maxSqr = Math.max(maxSqr, horizontalSqr(sample.localPos()));
        }
        for (TimedAction timed : actions) {
            switch (timed.action()) {
                case ChronoAction.BreakBlock a ->
                        maxSqr = Math.max(maxSqr, horizontalSqr(Vec3.atCenterOf(a.localPos())));
                case ChronoAction.PlaceBlock a ->
                        maxSqr = Math.max(maxSqr, horizontalSqr(Vec3.atCenterOf(a.localPos())));
                case ChronoAction.AttackEntity a ->
                        maxSqr = Math.max(maxSqr, horizontalSqr(a.localPos()));
                case ChronoAction.UseOnBlock a ->
                        maxSqr = Math.max(maxSqr, horizontalSqr(Vec3.atCenterOf(a.localPos())));
                case ChronoAction.UseContainer a ->
                        maxSqr = Math.max(maxSqr, horizontalSqr(a.target().localPoint()));
                case ChronoAction.InteractEntity a ->
                        maxSqr = Math.max(maxSqr, horizontalSqr(a.localPos()));
                case ChronoAction.UseItem ignored -> { }
            }
        }
        return Math.sqrt(maxSqr);
    }

    private static double horizontalSqr(Vec3 v) {
        return v.x * v.x + v.z * v.z;
    }
}
