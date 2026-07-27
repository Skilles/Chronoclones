package com.skilles.chronoclones.client;

import java.util.UUID;
import java.util.function.Supplier;

import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
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

/**
 * Draws the clone: the author's player model, translucent and tinted.
 *
 * <p>Both halves of "translucent" matter. The alpha is what stops a working farm from looking like a
 * crowd of players, and the cyan tint is what stops it reading as one <em>specific</em> player —
 * seeing what appears to be a named griefer standing in your base is a bad thirty seconds even when
 * nothing is wrong.
 *
 * <p>The skin is a lookup, not a dependency. {@code SkinManager.createLookup} hands back a supplier
 * that answers immediately with the UUID-derived default and swaps in the fetched skin when it
 * arrives, so the silhouette fallback the plan called for is the same code path as the real thing
 * rather than a separate one to maintain.
 *
 * <p>26.x rendering is state-extraction based: {@link #extractRenderState} fills a plain data object
 * on the main thread and {@link #submit} queues it. Nothing here may touch the entity — by the time
 * the model is posed, {@code ModelFeatureRenderer} is working from the state alone. See
 * {@link ChronoCloneRenderState} for the trap that makes the choice of state class load-bearing.
 */
public class ChronoCloneRenderer extends EntityRenderer<ChronoCloneEntity, ChronoCloneRenderState> {

    /** Solid enough to read as a body, sheer enough to never be mistaken for one. */
    private static final int TINT = 0x99_7FF5DC;

    private final HumanoidModel<ChronoCloneRenderState> wideModel;
    private final HumanoidModel<ChronoCloneRenderState> slimModel;
    private final ItemModelResolver itemModelResolver;

    public ChronoCloneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
        this.shadowStrength = 0.6f;
        this.itemModelResolver = context.getItemModelResolver();
        // The two player meshes differ only in arm width, and which one applies comes from the skin.
        this.wideModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER));
        this.slimModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
    }

    @Override
    public ChronoCloneRenderState createRenderState() {
        return new ChronoCloneRenderState();
    }

    @Override
    public void extractRenderState(ChronoCloneEntity entity, ChronoCloneRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);

        state.bodyRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        // Not the yaw again: HumanoidModel reads yRot as the head's rotation RELATIVE to the body,
        // which is already turned by bodyRot. Setting it to the absolute yaw turned the head twice,
        // so a ghost walking north — yaw 180 — faced its head due south while everything else about
        // it was correct. A recording carries one yaw for the whole body, so the head follows it.
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
    }

    @Override
    public void submit(ChronoCloneRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        HumanoidModel<ChronoCloneRenderState> model =
                state.skin.model() == PlayerModelType.SLIM ? this.slimModel : this.wideModel;
        RenderType renderType = RenderTypes.entityTranslucent(state.skin.body().texturePath());

        poseStack.pushPose();
        // The same three steps LivingEntityRenderer performs: face the body, flip into model space,
        // then drop the origin to the feet.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - state.bodyRot));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        collector.submitModel(model, state, poseStack, renderType,
                state.lightCoords, OverlayTexture.NO_OVERLAY, TINT, null, state.outlineColor, null);
        submitHeldItem(state, model, poseStack, collector);
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
    }

    /**
     * The held item, posed off the model's right hand.
     *
     * <p>Open-coded rather than reusing {@code ItemInHandLayer}: layers hang off
     * {@code LivingEntityRenderer}, and a ghost is not a living entity — deliberately, since that is
     * what keeps it off every {@code getEntitiesOfClass(LivingEntity...)} query in the game,
     * including our own attack targeting.
     *
     * <p>The item is drawn opaque. Tinting it too would make a held torch look broken rather than
     * ghostly, and the body already carries the effect.
     */
    private void submitHeldItem(ChronoCloneRenderState state, HumanoidModel<ChronoCloneRenderState> model,
                                PoseStack poseStack, SubmitNodeCollector collector) {
        if (state.rightHandItemState.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        model.translateToHand(state, HumanoidArm.RIGHT, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.translate(1.0f / 16.0f, 2.0f / 16.0f, -10.0f / 16.0f);

        state.rightHandItemState.submit(poseStack, collector, state.lightCoords,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    /**
     * The author's skin, defaulting to the UUID-derived silhouette until the fetch lands.
     *
     * <p>Cached on the entity: {@code createLookup} allocates a profile, a future handle and a
     * closure, and this runs once per ghost per frame.
     */
    private static PlayerSkin skinOf(ChronoCloneEntity entity) {
        UUID author = entity.authorId();
        if (author == null) {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        Supplier<PlayerSkin> lookup = entity.skinLookup(author);
        if (lookup == null) {
            lookup = Minecraft.getInstance().getSkinManager()
                    .createLookup(new GameProfile(author, entity.authorName()), false);
            entity.setSkinLookup(author, lookup);
        }
        return lookup.get();
    }
}
