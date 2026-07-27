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

/**
 * One line, and only when the goggles are showing you less than there is.
 *
 * <p>A reply carries whole recordings, so it is capped at the nearest few anchors and the server
 * says when it cut the list short. That flag was already computed, sent and cached — and read by
 * nothing, which made the cap exactly the silent truncation it was introduced to avoid. A view
 * missing half a base looks identical to a base with half as many anchors.
 */
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
        // Bottom centre, above the hotbar: the goggles' subject is the world, so this stays out of
        // the middle of it.
        graphics.centeredText(client.font,
                Component.translatable("hud.chronoclones.goggles.truncated", GogglePayloads.MAX_ANCHORS)
                        .withStyle(ChatFormatting.GRAY),
                graphics.guiWidth() / 2, graphics.guiHeight() - 55, 0xFFFFFFFF);
    }
}
