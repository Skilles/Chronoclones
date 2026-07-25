package com.skilles.chronoclones.client;

import com.skilles.chronoclones.entity.ChronoCloneEntity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * DAY 1 SPIKE renderer. Draws only a shadow, which is enough to watch the ghost travel its loop
 * and confirm the motion reads smoothly under the default entity tracker.
 *
 * <p>The translucent player model with the author's skin is Day 10 work, and the
 * silhouette fallback is what ships if skin fetching over-runs. Note that 26.x entity rendering is
 * also state-extraction based — {@code createRenderState} is the only abstract member, and drawing
 * happens in {@code submit}.
 */
@OnlyIn(Dist.CLIENT)
public class ChronoCloneRenderer extends EntityRenderer<ChronoCloneEntity, EntityRenderState> {

    public ChronoCloneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4f;
        this.shadowStrength = 0.6f;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
