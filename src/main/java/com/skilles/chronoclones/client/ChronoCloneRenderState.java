package com.skilles.chronoclones.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

public class ChronoCloneRenderState extends HumanoidRenderState {

    public PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
}
