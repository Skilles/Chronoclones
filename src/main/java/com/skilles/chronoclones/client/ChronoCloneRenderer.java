package com.skilles.chronoclones.client;

import java.util.UUID;

import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public class ChronoCloneRenderer extends EntityRenderer<ChronoCloneEntity, ChronoCloneRenderState> {

    private static final int TINT = 0x99_7FF5DC;

    private final HumanoidModel<ChronoCloneRenderState> wideModel;
    private final HumanoidModel<ChronoCloneRenderState> slimModel;
    private final ItemModelResolver itemModelResolver;

    public ChronoCloneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
        this.shadowStrength = 0.6f;
        this.itemModelResolver = context.getItemModelResolver();
        this.wideModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
        this.slimModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
    }

    @Override
    public @NonNull ChronoCloneRenderState createRenderState() {
        return new ChronoCloneRenderState();
    }

    @Override
    public void extractRenderState(@NonNull ChronoCloneEntity entity, @NonNull ChronoCloneRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);

        state.bodyRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        state.yRot = 0.0f;
        state.xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

        state.walkAnimationPos = entity.walkAnimation().position(partialTicks);
        state.walkAnimationSpeed = entity.walkAnimation().speed(partialTicks);

        state.skin = skinOf(entity);

        ItemStack held = entity.heldItem();
        state.rightHandItemStack = held;
        state.rightArmPose = held.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        this.itemModelResolver.updateForNonLiving(state.rightHandItemState, held,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);

        ItemStack offhand = entity.offhandItem();
        state.leftHandItemStack = offhand;
        state.leftArmPose = offhand.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        this.itemModelResolver.updateForNonLiving(state.leftHandItemState, offhand,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
    }

    @Override
    public void submit(ChronoCloneRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       @NonNull CameraRenderState camera) {
        HumanoidModel<ChronoCloneRenderState> model =
                state.skin.model() == PlayerModelType.SLIM ? this.slimModel : this.wideModel;
        RenderType renderType = RenderTypes.entityTranslucent(state.skin.body().texturePath());

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - state.bodyRot));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        collector.submitModel(model, state, poseStack, renderType,
                state.lightCoords, OverlayTexture.NO_OVERLAY, TINT, null, state.outlineColor, null);
        submitHeldItems(state, model, poseStack, collector);
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
    }

    private void submitHeldItems(ChronoCloneRenderState state,
                                 HumanoidModel<ChronoCloneRenderState> model,
                                 PoseStack poseStack, SubmitNodeCollector collector) {
        submitHeldItem(state, model, poseStack, collector, HumanoidArm.RIGHT);
        submitHeldItem(state, model, poseStack, collector, HumanoidArm.LEFT);
    }

    private void submitHeldItem(ChronoCloneRenderState state,
                                HumanoidModel<ChronoCloneRenderState> model,
                                PoseStack poseStack, SubmitNodeCollector collector,
                                HumanoidArm arm) {
        var item = arm == HumanoidArm.RIGHT ? state.rightHandItemState : state.leftHandItemState;
        if (item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        model.translateToHand(state, arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.translate(arm == HumanoidArm.RIGHT ? 1.0f / 16.0f : -1.0f / 16.0f,
                2.0f / 16.0f, -10.0f / 16.0f);

        item.submit(poseStack, collector, state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    private static PlayerSkin skinOf(ChronoCloneEntity entity) {
        UUID author = entity.authorId();
        return author == null ? DefaultPlayerSkin.getDefaultSkin() : AuthorSkins.of(author);
    }
}
