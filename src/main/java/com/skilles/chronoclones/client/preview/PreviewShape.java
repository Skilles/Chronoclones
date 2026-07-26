package com.skilles.chronoclones.client.preview;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A routine turned into something you can look at: boxes where it will act, and the path it walks.
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
        TRANSFER(0xFF_FFC24D);

        public final int colour;

        Kind(int colour) {
            this.colour = colour;
        }
    }

    public record Mark(BlockPos pos, Kind kind) {}

    private final List<Mark> marks;
    private final List<Vec3> path;

    private PreviewShape(List<Mark> marks, List<Vec3> path) {
        this.marks = marks;
        this.path = path;
    }

    public List<Mark> marks() {
        return marks;
    }

    /** The walked path in world space, already ordered; may be shorter than two points. */
    public List<Vec3> path() {
        return path;
    }

    public boolean isEmpty() {
        return marks.isEmpty() && path.size() < 2;
    }

    public static PreviewShape of(Recording recording, BlockPos anchorPos, Direction anchorFacing) {
        List<Mark> marks = new ArrayList<>();
        for (TimedAction timed : recording.actions()) {
            Kind kind = kindOf(timed.action());
            BlockPos local = blockOf(timed.action());
            if (kind == null || local == null) {
                continue;
            }
            marks.add(new Mark(LocalSpace.toWorld(local, anchorPos, anchorFacing), kind));
        }

        List<Vec3> path = new ArrayList<>(recording.motion().size());
        for (MotionSample sample : recording.motion()) {
            // Lifted slightly so the line rides above the floor rather than z-fighting with it.
            path.add(LocalSpace.toWorld(sample.localPos(), anchorPos, anchorFacing)
                    .add(0.5, 0.1, 0.5));
        }

        return new PreviewShape(List.copyOf(marks), List.copyOf(path));
    }

    /**
     * Actions with no fixed block have no box.
     *
     * <p>Using an item in mid-air, and attacking or interacting with an entity, all happen wherever
     * the clone is standing and whatever wandered into range — drawing a box at the recorded position
     * would assert a certainty the routine does not have.
     */
    private static @Nullable Kind kindOf(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock ignored -> Kind.BREAK;
            case ChronoAction.PlaceBlock ignored -> Kind.PLACE;
            case ChronoAction.UseOnBlock ignored -> Kind.INTERACT;
            case ChronoAction.UseContainer ignored -> Kind.TRANSFER;
            case ChronoAction.UseItem ignored -> null;
            case ChronoAction.AttackEntity ignored -> null;
            case ChronoAction.InteractEntity ignored -> null;
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
