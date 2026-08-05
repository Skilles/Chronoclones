//? if >=26 {
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
//?} else {
/*package com.skilles.chronoclones.client;

import java.util.UUID;

import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// The ghost is not a LivingEntity, so the humanoid model is driven by hand: the standard
// walk-cycle formulas over the entity's own walk animation, instead of setupAnim.
public class ChronoCloneRenderer extends EntityRenderer<ChronoCloneEntity> {

    private static final int TINT = 0x99_7FF5DC;

    private final HumanoidModel<LivingEntity> wideModel;
    private final HumanoidModel<LivingEntity> slimModel;

    public ChronoCloneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
        this.wideModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
        this.slimModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
    }

    @Override
    public ResourceLocation getTextureLocation(ChronoCloneEntity entity) {
        return skinOf(entity).texture();
    }

    @Override
    public void render(ChronoCloneEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        PlayerSkin skin = skinOf(entity);
        HumanoidModel<LivingEntity> model =
                skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.wideModel;

        float bodyRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        float limbPos = entity.walkAnimation().position(partialTicks);
        float limbSpeed = Math.min(entity.walkAnimation().speed(partialTicks), 1.0f);

        pose(model, headPitch, limbPos, limbSpeed,
                !entity.heldItem().isEmpty(), !entity.offhandItem().isEmpty());

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyRot));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

*///?}
//? if <26 {
//? if >=1.20.5 {
/*        model.renderToBuffer(poseStack,
                buffers.getBuffer(RenderType.entityTranslucent(skin.texture())),
                packedLight, OverlayTexture.NO_OVERLAY, TINT);
*///?} else {
/*        model.renderToBuffer(poseStack,
                buffers.getBuffer(RenderType.entityTranslucent(skin.texture())),
                packedLight, OverlayTexture.NO_OVERLAY,
                ((TINT >> 16) & 0xFF) / 255.0f, ((TINT >> 8) & 0xFF) / 255.0f,
                (TINT & 0xFF) / 255.0f, ((TINT >>> 24) & 0xFF) / 255.0f);
*///?}
//?}
//? if <26 {
/*

        renderHeldItem(entity.heldItem(), model, poseStack, buffers, packedLight, entity, HumanoidArm.RIGHT);
        renderHeldItem(entity.offhandItem(), model, poseStack, buffers, packedLight, entity, HumanoidArm.LEFT);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    private static void pose(HumanoidModel<LivingEntity> model, float headPitch,
                             float limbPos, float limbSpeed, boolean holdsMain, boolean holdsOff) {
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.attackTime = 0.0f;
        model.swimAmount = 0.0f;

        model.head.yRot = 0.0f;
        model.head.xRot = headPitch * ((float) Math.PI / 180.0f);
        model.hat.copyFrom(model.head);
        model.body.yRot = 0.0f;

        float swing = limbPos * 0.6662f;
        model.rightArm.xRot = Mth.cos(swing + (float) Math.PI) * 2.0f * limbSpeed * 0.5f;
        model.leftArm.xRot = Mth.cos(swing) * 2.0f * limbSpeed * 0.5f;
        model.rightArm.zRot = 0.0f;
        model.leftArm.zRot = 0.0f;
        model.rightLeg.xRot = Mth.cos(swing) * 1.4f * limbSpeed;
        model.leftLeg.xRot = Mth.cos(swing + (float) Math.PI) * 1.4f * limbSpeed;

        // The vanilla ITEM arm pose, lowered slightly toward the hip.
        if (holdsMain) {
            model.rightArm.xRot = model.rightArm.xRot * 0.5f - ((float) Math.PI / 10.0f);
        }
        if (holdsOff) {
            model.leftArm.xRot = model.leftArm.xRot * 0.5f - ((float) Math.PI / 10.0f);
        }
    }

    private void renderHeldItem(ItemStack stack, HumanoidModel<LivingEntity> model,
                                PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                ChronoCloneEntity entity, HumanoidArm arm) {
        if (stack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        model.translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.translate(arm == HumanoidArm.RIGHT ? 1.0f / 16.0f : -1.0f / 16.0f,
                2.0f / 16.0f, -10.0f / 16.0f);

        Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                arm == HumanoidArm.RIGHT
                        ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                        : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffers, entity.level(), 0);
        poseStack.popPose();
    }

    private static PlayerSkin skinOf(ChronoCloneEntity entity) {
        UUID author = entity.authorId();
        return author == null
                ? AuthorSkins.defaultSkin(entity.getUUID())
                : AuthorSkins.of(author);
    }
}
*///?}
