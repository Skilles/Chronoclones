package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.ChronoclonesConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side state of one in-progress recording.
 *
 * <p>Lives only on the server and only while capture is active; the finished {@link Recording} is
 * what gets written to the recorder item. Everything appended here is already converted to
 * anchor-local space via {@link LocalSpace}, so the origin and its cardinal facing are captured
 * once at start and never revisited.
 */
public final class RecordingSession {

    /** Why a session stopped, so the item can play the right sound and tell the player. */
    public enum StopReason {
        MANUAL,
        LENGTH_CAP,
        ACTION_CAP,
        ABANDONED
    }

    /** Identifies which recorder stack this session owns. See {@code RecordingProgress}. */
    private final UUID sessionId = UUID.randomUUID();

    private final UUID authorId;
    private final String authorName;
    private final BlockPos origin;
    private final Direction originFacing;

    private final List<MotionSample> motion = new ArrayList<>();
    private final List<TimedAction> actions = new ArrayList<>();

    private int tick;
    private boolean outOfRangeWarning;

    public RecordingSession(ServerPlayer player) {
        this.authorId = player.getUUID();
        this.authorName = player.getGameProfile().name();
        this.origin = player.blockPosition();
        // Snapping to a cardinal is mandatory — see LocalSpace for why arbitrary yaw cannot work.
        this.originFacing = LocalSpace.snapToCardinal(player.getYRot());
    }

    public UUID sessionId() {
        return sessionId;
    }

    public BlockPos origin() {
        return origin;
    }

    public Direction originFacing() {
        return originFacing;
    }

    public int tick() {
        return tick;
    }

    public int actionCount() {
        return actions.size();
    }

    public boolean outOfRangeWarning() {
        return outOfRangeWarning;
    }

    /** Cleared once the client has been shown the warning, so it flashes rather than sticks. */
    public void clearOutOfRangeWarning() {
        outOfRangeWarning = false;
    }

    private int maxRadius() {
        return ChronoclonesConfig.MAX_RADIUS.getAsInt();
    }

    /**
     * Advances one tick and samples motion on the sampling interval.
     *
     * @return the cap that was hit, or null to keep going
     */
    public StopReason tickAndSample(ServerPlayer player) {
        if (tick % MotionSample.SAMPLE_INTERVAL_TICKS == 0) {
            Vec3 local = LocalSpace.toLocal(player.position(), origin, originFacing);
            float localYaw = LocalSpace.toLocalYaw(player.getYRot(), originFacing);
            motion.add(new MotionSample(tick, local, localYaw, player.getXRot()));
        }

        tick++;

        if (tick >= ChronoclonesConfig.MAX_RECORDING_TICKS.getAsInt()) {
            return StopReason.LENGTH_CAP;
        }
        return null;
    }

    /**
     * Records an action if it is within range and under the action cap.
     *
     * <p>Out-of-range actions are dropped rather than clamped: silently relocating what the player
     * did would be worse than not recording it, and the HUD warns instead.
     *
     * @return the cap that was hit, or null
     */
    public StopReason record(ChronoAction action, Vec3 worldPos) {
        if (!withinRadius(worldPos)) {
            outOfRangeWarning = true;
            return null;
        }
        actions.add(new TimedAction(tick, action));

        if (actions.size() >= ChronoclonesConfig.MAX_ACTIONS.getAsInt()) {
            return StopReason.ACTION_CAP;
        }
        return null;
    }

    /** Index the next recorded action will land at, for {@link #dropActionAt}. */
    public int nextActionIndex() {
        return actions.size();
    }

    /**
     * Retracts an action that turned out not to be one.
     *
     * <p>Used for the click that opens a container: at click time it looks like an ordinary use, and
     * only the menu appearing afterwards reveals that the interesting part is what the player then
     * moves. Bounds-checked because the action may never have been recorded — out of range, or over
     * the cap.
     */
    public void dropActionAt(int index) {
        if (index >= 0 && index < actions.size()) {
            actions.remove(index);
        }
    }

    private boolean withinRadius(Vec3 worldPos) {
        double dx = worldPos.x - (origin.getX() + 0.5);
        double dz = worldPos.z - (origin.getZ() + 0.5);
        double dy = worldPos.y - origin.getY();
        int r = maxRadius();
        return dx * dx + dy * dy + dz * dz <= (double) r * r;
    }

    /** World -> local, for callers building actions. */
    public BlockPos toLocal(BlockPos worldPos) {
        return LocalSpace.toLocal(worldPos, origin, originFacing);
    }

    public Vec3 toLocal(Vec3 worldPos) {
        return LocalSpace.toLocal(worldPos, origin, originFacing);
    }

    public Direction toLocal(Direction worldFacing) {
        return LocalSpace.toLocal(worldFacing, originFacing);
    }

    public boolean isEmpty() {
        return motion.isEmpty() && actions.isEmpty();
    }

    public Recording finish() {
        return new Recording(List.copyOf(motion), List.copyOf(actions), Math.max(tick, 1), authorName, authorId);
    }
}
