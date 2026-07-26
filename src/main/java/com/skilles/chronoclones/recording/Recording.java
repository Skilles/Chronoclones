package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.phys.Vec3;

/**
 * A complete captured performance: a dense, evenly-sampled motion track plus a sparse,
 * event-driven action track.
 *
 * <p><b>{@code authorId} is not the anchor owner.</b> The author is whoever recorded this; the
 * owner is whoever runs it. Ghost appearance resolves from the author, and every piece of
 * attribution — block break events, damage sources, protection checks — resolves from the owner
 * stored on the block entity. Conflating them is a griefing vector: a shared
 * recording would otherwise break blocks attributed to someone who never consented. This record
 * therefore carries no owner field at all, so the mistake is not expressible here.
 *
 * <p>Serialization lives in {@link RecordingCodecs}.
 */
public record Recording(
        List<MotionSample> motion,
        List<TimedAction> actions,
        int lengthTicks,
        String authorName,
        UUID authorId) {

    public Recording {
        motion = List.copyOf(motion);
        actions = List.copyOf(actions);
    }

    public boolean isEmpty() {
        return motion.isEmpty() && actions.isEmpty();
    }

    public int lengthSeconds() {
        return lengthTicks / 20;
    }

    /** Action counts by type, for the shard tooltip. */
    public Map<ChronoActionType, Integer> actionCounts() {
        Map<ChronoActionType, Integer> counts = new java.util.EnumMap<>(ChronoActionType.class);
        for (TimedAction timed : actions) {
            counts.merge(timed.action().type(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Furthest horizontal reach of the routine from the anchor, for the shard tooltip. Handing
     * someone an opaque item that turns out to mine a shaft under their base is an actual attack
     * on a shared server, so this must be inspectable before imprinting.
     */
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
                        maxSqr = Math.max(maxSqr, horizontalSqr(Vec3.atCenterOf(a.localPos())));
                case ChronoAction.InteractEntity a ->
                        maxSqr = Math.max(maxSqr, horizontalSqr(a.localPos()));
                // Using an item in mid-air happens wherever the clone is standing, which the motion
                // samples already account for.
                case ChronoAction.UseItem ignored -> { }
            }
        }
        return Math.sqrt(maxSqr);
    }

    private static double horizontalSqr(Vec3 v) {
        return v.x * v.x + v.z * v.z;
    }
}
