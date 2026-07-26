package com.skilles.chronoclones.client.preview;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.replay.ActionExecutor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A routine turned into something you can look at: boxes where it will act, spheres where it will
 * swing, and the path it walks.
 *
 * <p>The point is to answer "what is this thing about to do to my base?" <em>before</em> imprinting
 * it, which the design treats as a safety requirement rather than a nicety — a shard handed to you
 * on a shared server is untrusted code, and a tooltip saying "14 breaks" does not tell you whether
 * those breaks are the cobblestone wall or the floor under your chests.
 *
 * <p>Computed against an anchor position and facing, so the preview is rotated exactly as the
 * routine would be if imprinted there. Looking at a west-facing anchor shows you the west-facing
 * version.
 */
public final class PreviewShape {

    /** What a routine does at one block, coloured by how much it should worry you. */
    public enum Kind {
        /** Removes a block. */
        BREAK(0xFF_FF6B4A),
        /** Adds a block. */
        PLACE(0xFF_7CFF9B),
        /** Right-clicks something. */
        INTERACT(0xFF_86FFE7),
        /** Moves items in or out of a container. */
        TRANSFER(0xFF_FFC24D),
        /** Swings at whatever is in range. */
        ATTACK(0xFF_FF3B3B);

        public final int colour;

        Kind(int colour) {
            this.colour = colour;
        }
    }

    /** Drawn over the step an anchor is currently stuck on, whatever that step normally looks like. */
    public static final int FAILING_COLOUR = 0xFF_FF2020;

    /**
     * A block the routine acts on.
     *
     * @param failing whether this is the step the anchor is currently stuck on
     */
    public record Mark(BlockPos pos, Kind kind, boolean failing) {}

    /**
     * A region the routine reaches into without naming a block.
     *
     * <p>Attacking and interacting with a mob happen wherever the clone is standing and to whatever
     * wandered into range. A box would assert a certainty the routine does not have; a sphere the
     * size of the executor's own query says the true thing — it swings here, at whatever is in this
     * volume.
     */
    public record Volume(Vec3 centre, double radius, Kind kind, boolean failing) {}

    private final List<Mark> marks;
    private final List<Volume> volumes;
    private final List<Vec3> path;

    private PreviewShape(List<Mark> marks, List<Volume> volumes, List<Vec3> path) {
        this.marks = marks;
        this.volumes = volumes;
        this.path = path;
    }

    public List<Mark> marks() {
        return marks;
    }

    public List<Volume> volumes() {
        return volumes;
    }

    /** The walked path in world space, already ordered; may be shorter than two points. */
    public List<Vec3> path() {
        return path;
    }

    public boolean isEmpty() {
        return marks.isEmpty() && volumes.isEmpty() && path.size() < 2;
    }

    public static PreviewShape of(Recording recording, BlockPos anchorPos, Direction anchorFacing) {
        return of(recording, anchorPos, anchorFacing, null);
    }

    /**
     * @param failingLocal anchor-local position of the step that is currently failing, or null.
     *                     Marked rather than filtered out: the value is in seeing <em>which</em> of
     *                     fourteen breaks is the one the anchor cannot do.
     */
    public static PreviewShape of(Recording recording, BlockPos anchorPos, Direction anchorFacing,
                                  @Nullable BlockPos failingLocal) {
        List<Mark> marks = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();

        for (TimedAction timed : recording.actions()) {
            Kind kind = kindOf(timed.action());
            if (kind == null) {
                continue;
            }

            Vec3 reach = reachOf(timed.action());
            if (reach != null) {
                // No half-block offset: an entity action records an exact point rather than a
                // square, and the executor inflates its query around that same point.
                volumes.add(new Volume(LocalSpace.toWorld(reach, anchorPos, anchorFacing),
                        radiusOf(timed.action()), kind,
                        BlockPos.containing(reach).equals(failingLocal)));
                continue;
            }

            BlockPos local = blockOf(timed.action());
            if (local == null) {
                continue;
            }
            marks.add(new Mark(LocalSpace.toWorld(local, anchorPos, anchorFacing), kind,
                    local.equals(failingLocal)));
        }

        List<Vec3> path = new ArrayList<>(recording.motion().size());
        for (MotionSample sample : recording.motion()) {
            // Lifted slightly so the line rides above the floor rather than z-fighting with it.
            path.add(LocalSpace.toWorld(sample.localPos(), anchorPos, anchorFacing)
                    .add(0.5, 0.1, 0.5));
        }

        return new PreviewShape(List.copyOf(marks), List.copyOf(volumes), List.copyOf(path));
    }

    /**
     * Where an action reaches when it does not name a block, or null if it names one.
     *
     * <p>Using an item in mid-air stays absent entirely: it happens wherever the clone is standing,
     * affects nothing in particular, and a sphere for it would clutter the view with the one action
     * type that cannot touch anything.
     */
    private static @Nullable Vec3 reachOf(ChronoAction action) {
        return switch (action) {
            case ChronoAction.AttackEntity a -> a.localPos();
            case ChronoAction.InteractEntity a -> a.localPos();
            default -> null;
        };
    }

    /** The executor's own query radius, so the sphere is the region it will really search. */
    private static double radiusOf(ChronoAction action) {
        return action instanceof ChronoAction.AttackEntity
                ? ActionExecutor.ATTACK_RADIUS
                : ActionExecutor.INTERACT_RADIUS;
    }

    private static @Nullable Kind kindOf(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock ignored -> Kind.BREAK;
            case ChronoAction.PlaceBlock ignored -> Kind.PLACE;
            case ChronoAction.UseOnBlock ignored -> Kind.INTERACT;
            case ChronoAction.UseContainer ignored -> Kind.TRANSFER;
            case ChronoAction.AttackEntity ignored -> Kind.ATTACK;
            case ChronoAction.InteractEntity ignored -> Kind.INTERACT;
            case ChronoAction.UseItem ignored -> null;
        };
    }

    private static @Nullable BlockPos blockOf(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock a -> a.localPos();
            case ChronoAction.PlaceBlock a -> a.localPos();
            case ChronoAction.UseOnBlock a -> a.localPos();
            case ChronoAction.UseContainer a -> a.localPos();
            default -> null;
        };
    }
}
