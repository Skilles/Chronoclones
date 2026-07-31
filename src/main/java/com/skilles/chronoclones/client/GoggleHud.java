package com.skilles.chronoclones.client;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.preview.GoggleCache;
import com.skilles.chronoclones.network.GogglePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public final class GoggleHud {

    private GoggleHud() {}

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, Chronoclones.id("goggle_truncated"), GoggleHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden() || !GoggleCache.isTruncated()) {
            return;
        }
        graphics.centeredText(client.font,
                Component.translatable("hud.chronoclones.goggles.truncated", GogglePayloads.MAX_ANCHORS)
                        .withStyle(ChatFormatting.GRAY),
                graphics.guiWidth() / 2, graphics.guiHeight() - 55, 0xFFFFFFFF);
    }
}
