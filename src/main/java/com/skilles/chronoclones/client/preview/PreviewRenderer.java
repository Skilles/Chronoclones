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
    private static final float PATH_LINE_WIDTH = 2.0f;
    private static final int PATH_COLOUR = 0xCC_86FFE7;
    /** Boxes are inset slightly so they sit inside the block rather than fighting its faces. */
    private static final double INSET = 0.002;

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        PreviewCache.Target target = PreviewCache.current();
        if (target == null) {
            return;
        }

        PreviewShape shape = PreviewShape.of(target.recording(), target.anchorPos(), target.facing());
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
        submitPath(collector, poseStack, shape.path());

        poseStack.popPose();
    }

    private static void submitBox(SubmitNodeCollector collector, PoseStack poseStack,
                                  PreviewShape.Mark mark) {
        BlockPos pos = mark.pos();
        poseStack.pushPose();
        poseStack.translate(pos.getX() + INSET, pos.getY() + INSET, pos.getZ() + INSET);
        poseStack.scale(1.0f - (float) (INSET * 2), 1.0f - (float) (INSET * 2), 1.0f - (float) (INSET * 2));

        // afterTerrain = true so the outline shows through the very blocks it describes. A preview
        // you can only see by standing in the right place is not a preview.
        collector.submitShapeOutline(poseStack, Shapes.block(), RenderTypes.lines(),
                mark.kind().colour, BOX_LINE_WIDTH, true);

        poseStack.popPose();
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
                emitLine(buffer, pose, normal, from, to);
            }
        });
    }

    private static void emitLine(VertexConsumer buffer, PoseStack.Pose pose, Vector3f normal,
                                 Vec3 from, Vec3 to) {
        normal.set((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() < 1.0e-6f) {
            // Two samples at the same place. The line shader normalises, and a zero normal is NaN.
            return;
        }
        normal.normalize();

        buffer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(PATH_COLOUR).setNormal(pose, normal).setLineWidth(PATH_LINE_WIDTH);
        buffer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(PATH_COLOUR).setNormal(pose, normal).setLineWidth(PATH_LINE_WIDTH);
    }
}
