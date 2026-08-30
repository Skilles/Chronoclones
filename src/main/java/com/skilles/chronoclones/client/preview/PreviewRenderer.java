//? if >=26 {
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
import org.joml.Vector3f;

public final class PreviewRenderer {

    private PreviewRenderer() {}

    private static final float BOX_LINE_WIDTH = 3.0f;
    private static final float FAILING_LINE_WIDTH = 5.0f;
    private static final float PATH_LINE_WIDTH = 2.0f;
    private static final int PATH_COLOUR = 0xCC_86FFE7;
    private static final int RING_SEGMENTS = 12;
    private static final int GOGGLE_ALPHA = 0x55;
    private static final double INSET = 0.002;

    /** Called from the loader's world-render custom-geometry stage. */
    public static void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector) {
        PreviewCache.Target hovered = PreviewCache.current();
        List<PreviewCache.Target> worn = GoggleCache.current();
        if (hovered == null && worn.isEmpty()) {
            return;
        }

        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (PreviewCache.Target target : worn) {
            if (hovered == null || !target.anchorPos().equals(hovered.anchorPos())) {
                submit(collector, poseStack, target, GOGGLE_ALPHA);
            }
        }
        if (hovered != null) {
            submit(collector, poseStack, hovered, 0xFF);
        }

        poseStack.popPose();
    }

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack,
                               PreviewCache.Target target, int alpha) {
        PreviewShape shape = PreviewShape.of(target.recording(), target.placement().origin(),
                target.effectiveFacing(),
                target.failure().isFailure() ? target.failure().localPos() : null);
        if (shape.isEmpty()) {
            return;
        }

        for (PreviewShape.Mark mark : shape.marks()) {
            submitBox(collector, poseStack, mark, alpha);
        }
        for (PreviewShape.Volume volume : shape.volumes()) {
            submitVolume(collector, poseStack, volume, alpha);
        }
        submitPath(collector, poseStack, shape.path(), alpha);
    }

    private static int fade(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (alpha << 24);
    }

    private static void submitBox(SubmitNodeCollector collector, PoseStack poseStack,
                                  PreviewShape.Mark mark, int alpha) {
        BlockPos pos = mark.pos();
        poseStack.pushPose();
        poseStack.translate(pos.getX() + INSET, pos.getY() + INSET, pos.getZ() + INSET);
        poseStack.scale(1.0f - (float) (INSET * 2), 1.0f - (float) (INSET * 2), 1.0f - (float) (INSET * 2));

        int colour = fade(mark.failing() ? PreviewShape.FAILING_COLOUR : mark.kind().colour, alpha);
        float width = mark.failing() ? FAILING_LINE_WIDTH : BOX_LINE_WIDTH;

        collector.submitShapeOutline(poseStack, Shapes.block(), RenderTypes.lines(),
                colour, width, true);

        poseStack.popPose();
    }

    private static void submitVolume(SubmitNodeCollector collector, PoseStack poseStack,
                                     PreviewShape.Volume volume, int alpha) {
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
                        emitLine(buffer, pose, normal, previous, point, fade(volume.failing()
                                ? PreviewShape.FAILING_COLOUR
                                : volume.kind().colour, alpha));
                    }
                    previous = point;
                }
            }
        });
    }

    private static void submitPath(SubmitNodeCollector collector, PoseStack poseStack,
                                   List<Vec3> path, int alpha) {
        if (path.size() < 2) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            Vector3f normal = new Vector3f();
            for (int i = 0; i < path.size() - 1; i++) {
                Vec3 from = path.get(i);
                Vec3 to = path.get(i + 1);
                emitLine(buffer, pose, normal, from, to, fade(PATH_COLOUR, alpha));
            }
        });
    }

    private static void emitLine(VertexConsumer buffer, PoseStack.Pose pose, Vector3f normal,
                                 Vec3 from, Vec3 to, int colour) {
        normal.set((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() < 1.0e-6f) {
            return;
        }
        normal.normalize();

        buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(colour).setNormal(pose, normal).setLineWidth(PATH_LINE_WIDTH);
        buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(colour).setNormal(pose, normal).setLineWidth(PATH_LINE_WIDTH);
    }
}
//?} else {
/*package com.skilles.chronoclones.client.preview;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Vector3f;

// The pre-26 line pipeline: one lines buffer from the frame's buffer source, no per-line width.
public final class PreviewRenderer {

    private PreviewRenderer() {}

    private static final int PATH_COLOUR = 0xCC_86FFE7;
    private static final int RING_SEGMENTS = 12;
    private static final int GOGGLE_ALPHA = 0x55;
    private static final double INSET = 0.002;

    public static void renderGeometry(PoseStack poseStack, MultiBufferSource buffers) {
        PreviewCache.Target hovered = PreviewCache.current();
        List<PreviewCache.Target> worn = GoggleCache.current();
        if (hovered == null && worn.isEmpty()) {
            return;
        }

        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (PreviewCache.Target target : worn) {
            if (hovered == null || !target.anchorPos().equals(hovered.anchorPos())) {
                render(buffer, poseStack, target, GOGGLE_ALPHA);
            }
        }
        if (hovered != null) {
            render(buffer, poseStack, hovered, 0xFF);
        }

        poseStack.popPose();
    }

    private static void render(VertexConsumer buffer, PoseStack poseStack,
                               PreviewCache.Target target, int alpha) {
        PreviewShape shape = PreviewShape.of(target.recording(), target.placement().origin(),
                target.effectiveFacing(),
                target.failure().isFailure() ? target.failure().localPos() : null);
        if (shape.isEmpty()) {
            return;
        }

        for (PreviewShape.Mark mark : shape.marks()) {
            renderBox(buffer, poseStack, mark, alpha);
        }
        for (PreviewShape.Volume volume : shape.volumes()) {
            renderVolume(buffer, poseStack, volume, alpha);
        }
        renderPath(buffer, poseStack, shape.path(), alpha);
    }

    private static int fade(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (alpha << 24);
    }

    private static void renderBox(VertexConsumer buffer, PoseStack poseStack,
                                  PreviewShape.Mark mark, int alpha) {
        BlockPos pos = mark.pos();
        int colour = fade(mark.failing() ? PreviewShape.FAILING_COLOUR : mark.kind().colour, alpha);
        float r = ((colour >> 16) & 0xFF) / 255.0f;
        float g = ((colour >> 8) & 0xFF) / 255.0f;
        float b = (colour & 0xFF) / 255.0f;
        float a = ((colour >>> 24) & 0xFF) / 255.0f;

        LevelRenderer.renderLineBox(poseStack, buffer,
                pos.getX() + INSET, pos.getY() + INSET, pos.getZ() + INSET,
                pos.getX() + 1 - INSET, pos.getY() + 1 - INSET, pos.getZ() + 1 - INSET, r, g, b, a);
    }

    private static void renderVolume(VertexConsumer buffer, PoseStack poseStack,
                                     PreviewShape.Volume volume, int alpha) {
        Vector3f normal = new Vector3f();
        PoseStack.Pose pose = poseStack.last();
        int colour = fade(volume.failing() ? PreviewShape.FAILING_COLOUR : volume.kind().colour, alpha);
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
                    emitLine(buffer, pose, normal, previous, point, colour);
                }
                previous = point;
            }
        }
    }

    private static void renderPath(VertexConsumer buffer, PoseStack poseStack,
                                   List<Vec3> path, int alpha) {
        if (path.size() < 2) {
            return;
        }
        Vector3f normal = new Vector3f();
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < path.size() - 1; i++) {
            emitLine(buffer, pose, normal, path.get(i), path.get(i + 1), fade(PATH_COLOUR, alpha));
        }
    }

    private static void emitLine(VertexConsumer buffer, PoseStack.Pose pose, Vector3f normal,
                                 Vec3 from, Vec3 to, int colour) {
        normal.set((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() < 1.0e-6f) {
            return;
        }
        normal.normalize();

*///?}
//? if <26 {
//? if >=1.21 {
/*        buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(colour).setNormal(pose, normal.x, normal.y, normal.z);
        buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(colour).setNormal(pose, normal.x, normal.y, normal.z);
*///?} else {
/*        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int a = (colour >>> 24) & 0xFF;
        buffer.vertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z)
                .color(r, g, b, a).normal(pose.normal(), normal.x, normal.y, normal.z).endVertex();
        buffer.vertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z)
                .color(r, g, b, a).normal(pose.normal(), normal.x, normal.y, normal.z).endVertex();
*///?}
//?}
//? if <26 {
/*    }
}
*///?}
