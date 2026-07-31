package com.skilles.chronoclones.client.preview;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A routine as geometry: boxes where it acts, spheres where it swings, and its path. */
public final class PreviewShape {

    public enum Kind {

        BREAK(0xFF_FF6B4A),
        PLACE(0xFF_7CFF9B),
        INTERACT(0xFF_86FFE7),
        TRANSFER(0xFF_FFC24D),
        ATTACK(0xFF_FF3B3B);

        public final int colour;

        Kind(int colour) {
            this.colour = colour;
        }
    }

    public static final int FAILING_COLOUR = 0xFF_FF2020;

    public record Mark(BlockPos pos, Kind kind, boolean failing) {}

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

    public List<Vec3> path() {
        return path;
    }

    public boolean isEmpty() {
        return marks.isEmpty() && volumes.isEmpty() && path.size() < 2;
    }

    public static PreviewShape of(Recording recording, BlockPos anchorPos, Direction anchorFacing) {
        return of(recording, anchorPos, anchorFacing, null);
    }

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
                volumes.add(new Volume(LocalSpace.toWorld(reach, anchorPos, anchorFacing),
                        radiusOf(timed), kind,
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
            path.add(LocalSpace.toWorld(sample.localPos(), anchorPos, anchorFacing)
                    .add(0.5, 0.1, 0.5));
        }

        return new PreviewShape(List.copyOf(marks), List.copyOf(volumes), List.copyOf(path));
    }

    private static @Nullable Vec3 reachOf(ChronoAction action) {
        return switch (action) {
            case ChronoAction.AttackEntity a -> a.localPos();
            case ChronoAction.InteractEntity a -> a.localPos();
            case ChronoAction.UseContainer a when a.target() instanceof MenuTarget.Entity entity ->
                    entity.localPos();
            default -> null;
        };
    }

    private static double radiusOf(TimedAction timed) {
        return timed.settings().target().radius();
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
            case ChronoAction.UseContainer a -> a.target().localBlock();
            default -> null;
        };
    }
}
