package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.skilles.chronoclones.ChronoclonesConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A recording in progress. */
public final class RecordingSession {

    public enum StopReason {

        MANUAL,
        LENGTH_CAP,
        ACTION_CAP,
        STEP_CAP,
        ABANDONED
    }

    private final UUID sessionId = UUID.randomUUID();

    private final UUID authorId;
    private final String authorName;
    private final BlockPos origin;
    private final Direction originFacing;
    private final boolean creative;

    private final List<MotionSample> motion = new ArrayList<>();
    private final List<AttackIntent.Swing> actions = new ArrayList<>();

    private final Set<UUID> killed = new HashSet<>();

    private int tick;
    private boolean outOfRangeWarning;

    public RecordingSession(ServerPlayer player) {
        this.authorId = player.getUUID();
        this.authorName = player.getGameProfile().name();
        this.origin = player.blockPosition();
        this.originFacing = LocalSpace.snapToCardinal(player.getYRot());
        this.creative = player.isCreative();
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

    public void clearOutOfRangeWarning() {
        outOfRangeWarning = false;
    }

    private int maxRadius() {
        return ChronoclonesConfig.maxRadius();
    }

    public StopReason tickAndSample(ServerPlayer player) {
        if (tick % MotionSample.SAMPLE_INTERVAL_TICKS == 0) {
            Vec3 local = LocalSpace.toLocal(player.position(), origin, originFacing);
            float localYaw = LocalSpace.toLocalYaw(player.getYRot(), originFacing);
            motion.add(new MotionSample(tick, local, localYaw, player.getXRot()));
        }

        tick++;

        if (tick >= ChronoclonesConfig.maxRecordingTicks()) {
            return StopReason.LENGTH_CAP;
        }
        return null;
    }

    public StopReason record(ChronoAction action, Vec3 worldPos, int heldSlot) {
        return record(action, worldPos, heldSlot, null);
    }

    public StopReason record(ChronoAction action, Vec3 worldPos, int heldSlot, @Nullable UUID target) {
        if (!withinRadius(worldPos)) {
            outOfRangeWarning = true;
            return null;
        }
        actions.add(new AttackIntent.Swing(new TimedAction(tick, action, heldSlot), target));

        if (actions.size() >= ChronoclonesConfig.maxActions()) {
            return StopReason.ACTION_CAP;
        }
        return null;
    }

    /**
     * Gives the most recent use the time it was actually held.
     *
     * <p>A use is recorded when the click arrives, before anyone knows how long it will last:
     * drawing a bow, eating, and throwing a snowball are the same event until it ends.
     */
    public boolean noteHeldFor(int ticks) {
        for (int index = actions.size() - 1; index >= 0; index--) {
            AttackIntent.Swing swing = actions.get(index);
            TimedAction timed = swing.timed();
            if (!(timed.action() instanceof ChronoAction.UseItem use)) {
                continue;
            }
            actions.set(index, new AttackIntent.Swing(
                    new TimedAction(timed.tick(), use.heldFor(ticks), timed.settings()),
                    swing.target()));
            return true;
        }
        return false;
    }

    public int nextActionIndex() {
        return actions.size();
    }

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

    public BlockPos toLocal(BlockPos worldPos) {
        return LocalSpace.toLocal(worldPos, origin, originFacing);
    }

    public Vec3 toLocal(Vec3 worldPos) {
        return LocalSpace.toLocal(worldPos, origin, originFacing);
    }

    public Direction toLocal(Direction worldFacing) {
        return LocalSpace.toLocal(worldFacing, originFacing);
    }

    public void noteDeath(UUID entityId) {
        killed.add(entityId);
    }

    public boolean isEmpty() {
        return motion.isEmpty() && actions.isEmpty();
    }

    public Recording finish() {
        return new Recording(List.copyOf(motion), AttackIntent.coalesce(actions, killed),
                Math.max(tick, 1), authorName, authorId, creative);
    }
}
