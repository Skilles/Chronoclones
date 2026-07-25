package com.skilles.chronoclones.client;

import java.util.UUID;
import java.util.function.Supplier;

import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

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
 * the model is posed, {@code ModelFeatureRenderer} is working from the state alone.
 */
public class ChronoCloneRenderer extends EntityRenderer<ChronoCloneEntity, AvatarRenderState> {

    /** Solid enough to read as a body, sheer enough to never be mistaken for one. */
    private static final int TINT = 0xB0_7FF5DC;

    private final PlayerModel wideModel;
    private final PlayerModel slimModel;

    public ChronoCloneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
        this.shadowStrength = 0.6f;
        this.wideModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(ChronoCloneEntity entity, AvatarRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);

        state.yRot = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        state.bodyRot = state.yRot;
        state.xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

        state.walkAnimationPos = entity.walkAnimation().position(partialTicks);
        state.walkAnimationSpeed = entity.walkAnimation().speed(partialTicks);

        state.skin = skinOf(entity);

        // A ghost has no cape and no shoulder parrots; leaving the cape enabled would render a cape
        // the author is wearing, floating on a body that is deliberately not them.
        state.showCape = false;
    }

    @Override
    public void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        PlayerModel model = state.skin.model() == PlayerModelType.SLIM ? this.slimModel : this.wideModel;
        RenderType renderType = RenderTypes.entityTranslucent(state.skin.body().texturePath());

        poseStack.pushPose();
        // The same three steps LivingEntityRenderer performs: face the body, flip into model space,
        // then drop the origin to the feet.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - state.bodyRot));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        collector.submitModel(model, state, poseStack, renderType,
                state.lightCoords, OverlayTexture.NO_OVERLAY, TINT, null, state.outlineColor, null);
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
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
