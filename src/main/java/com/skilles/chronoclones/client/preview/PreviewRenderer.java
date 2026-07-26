package com.skilles.chronoclones.client.preview;

import java.util.List;

import com.skilles.chronoclones.Chronoclones;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

/**
 * Draws the preview: a box at every block the routine touches, and a line along the path it walks.
 *
 * <p>Boxes rather than a rehearsal ghost. A ghost shows one moment at a time and you have to watch
 * the whole loop to learn what it does; the boxes show every block it will ever touch, at once, in
 * colours that separate "removes a block" from "adds one". For deciding whether to trust a stranger's
 * shard, all-at-once is the only useful view.
 */
@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public final class PreviewRenderer {

    private PreviewRenderer() {}

    private static final float BOX_LINE_WIDTH = 3.0f;
    /** Heavier than the rest, so the stuck step reads before you have looked at anything else. */
    private static final float FAILING_LINE_WIDTH = 5.0f;
    private static final float PATH_LINE_WIDTH = 2.0f;
    private static final int PATH_COLOUR = 0xCC_86FFE7;
    /** Segments per ring of a reach sphere. Twelve is round enough at the distances involved. */
    private static final int RING_SEGMENTS = 12;
    /** Boxes are inset slightly so they sit inside the block rather than fighting its faces. */
    private static final double INSET = 0.002;

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        PreviewCache.Target target = PreviewCache.current();
        if (target == null) {
            return;
        }

        // Drawn from the nudged origin, so what you see is where the work actually lands.
        PreviewShape shape = PreviewShape.of(target.recording(), target.placement().origin(),
                target.facing(), target.failure().isFailure() ? target.failure().localPos() : null);
        if (shape.isEmpty()) {
            return;
        }

        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        poseStack.pushPose();
        // Everything below is in world coordinates; one translation puts the whole preview into
        // camera space rather than each piece doing its own subtraction.
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (PreviewShape.Mark mark : shape.marks()) {
            submitBox(collector, poseStack, mark);
        }
        for (PreviewShape.Volume volume : shape.volumes()) {
            submitVolume(collector, poseStack, volume);
        }
        submitPath(collector, poseStack, shape.path());

        poseStack.popPose();
    }

    private static void submitBox(SubmitNodeCollector collector, PoseStack poseStack,
                                  PreviewShape.Mark mark) {
        BlockPos pos = mark.pos();
        poseStack.pushPose();
        poseStack.translate(pos.getX() + INSET, pos.getY() + INSET, pos.getZ() + INSET);
        poseStack.scale(1.0f - (float) (INSET * 2), 1.0f - (float) (INSET * 2), 1.0f - (float) (INSET * 2));

        // The failing step overrides its own colour and draws heavier. Otherwise "which of these
        // fourteen breaks is the one that is stuck" is a question you answer by counting.
        int colour = mark.failing() ? PreviewShape.FAILING_COLOUR : mark.kind().colour;
        float width = mark.failing() ? FAILING_LINE_WIDTH : BOX_LINE_WIDTH;

        // afterTerrain = true so the outline shows through the very blocks it describes. A preview
        // you can only see by standing in the right place is not a preview.
        collector.submitShapeOutline(poseStack, Shapes.block(), RenderTypes.lines(),
                colour, width, true);

        poseStack.popPose();
    }

    /**
     * A reach volume, as three orthogonal circles.
     *
     * <p>Three rings rather than a mesh: it costs a few dozen line segments, reads unmistakably as a
     * sphere from any angle, and stays legible when several overlap — which they will, because an
     * attack routine is usually many swings from one spot.
     */
    private static void submitVolume(SubmitNodeCollector collector, PoseStack poseStack,
                                     PreviewShape.Volume volume) {
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            Vector3f normal = new Vector3f();
            for (int axis = 0; axis < 3; axis++) {
                Vec3 previous = null;
                for (int step = 0; step <= RING_SEGMENTS; step++) {
                    double angle = (step / (double) RING_SEGMENTS) * Math.PI * 2.0;
                    double a = Math.cos(angle) * volume.radius();
                    double b = Math.sin(angle) * volume.radius();
                    Vec3 point = switch (axis) {
                        case 0 -> volume.centre().add(a, b, 0.0);
                        case 1 -> volume.centre().add(a, 0.0, b);
                        default -> volume.centre().add(0.0, a, b);
                    };
                    if (previous != null) {
                        emitLine(buffer, pose, normal, previous, point, volume.failing()
                                ? PreviewShape.FAILING_COLOUR
                                : volume.kind().colour);
                    }
                    previous = point;
                }
            }
        });
    }

    private static void submitPath(SubmitNodeCollector collector, PoseStack poseStack, List<Vec3> path) {
        if (path.size() < 2) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            Vector3f normal = new Vector3f();
            for (int i = 0; i < path.size() - 1; i++) {
                Vec3 from = path.get(i);
                Vec3 to = path.get(i + 1);
                emitLine(buffer, pose, normal, from, to, PATH_COLOUR);
            }
        });
    }

    private static void emitLine(VertexConsumer buffer, PoseStack.Pose pose, Vector3f normal,
                                 Vec3 from, Vec3 to, int colour) {
        normal.set((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() < 1.0e-6f) {
            // Two samples at the same place. The line shader normalises, and a zero normal is NaN.
            return;
        }
        normal.normalize();

        buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(colour).setNormal(pose, normal).setLineWidth(PATH_LINE_WIDTH);
        buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(colour).setNormal(pose, normal).setLineWidth(PATH_LINE_WIDTH);
    }
}
