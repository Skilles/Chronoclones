package com.skilles.chronoclones.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Render state for a clone.
 *
 * <p>Not an {@code AvatarRenderState}: the dispatcher resolves the renderer from the state, so
 * anything extending that one silently renders as an ordinary player instead.
 */
public class ChronoCloneRenderState extends HumanoidRenderState {

    /** The author's skin: supplies both the texture and whether the arms are slim. */
    public PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
}
