package com.skilles.chronoclones.client;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.preview.GoggleCache;
import com.skilles.chronoclones.network.GogglePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class GoggleHud {

    private GoggleHud() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        //? if >=26 {
        if (client.player == null || client.gui.hud.isHidden() || !GoggleCache.isTruncated()) {
        //?} else {
        /*if (client.player == null || client.options.hideGui || !GoggleCache.isTruncated()) {
        *///?}
            return;
        }
        graphics.centeredText(client.font,
                Component.translatable("hud.chronoclones.goggles.truncated", GogglePayloads.MAX_ANCHORS)
                        .withStyle(ChatFormatting.GRAY),
                graphics.guiWidth() / 2, graphics.guiHeight() - 55, 0xFFFFFFFF);
    }
}
