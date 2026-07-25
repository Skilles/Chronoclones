package com.skilles.chronoclones.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Render state for a clone.
 *
 * <p><b>Deliberately not an {@code AvatarRenderState}, and that is the whole reason this class
 * exists.</b> {@code EntityRenderDispatcher.getRenderer(S renderState)} resolves the renderer from
 * the <em>state</em>, and its first branch is {@code instanceof AvatarRenderState} → hand it to
 * vanilla's player renderer. A modded renderer that returns an {@code AvatarRenderState} therefore
 * has its {@code extractRenderState} called and its {@code submit} silently ignored: the entity
 * renders as an ordinary opaque player, with no hint that any custom drawing code was skipped.
 *
 * <p>Extending {@link HumanoidRenderState} instead keeps the humanoid animation rig — which is all
 * {@code PlayerModel} adds beyond {@code HumanoidModel} anyway, since the hat, jacket, sleeve and
 * trouser overlays are children of the humanoid parts and render with them.
 */
public class ChronoCloneRenderState extends HumanoidRenderState {

    /** The author's skin: supplies both the texture and whether the arms are slim. */
    public PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
}
