//? if neoforge {
package com.skilles.chronoclones.platform.neoforge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.ChronoCloneRenderer;
import com.skilles.chronoclones.client.ChronoclonesClientInit;
import com.skilles.chronoclones.client.GoggleHud;
import com.skilles.chronoclones.client.NudgeKeys;
import com.skilles.chronoclones.client.RecordingHighlights;
import com.skilles.chronoclones.client.RecordingHud;
import com.skilles.chronoclones.client.preview.PreviewRenderer;
import com.skilles.chronoclones.menu.client.ChronoAnchorScreen;
import com.skilles.chronoclones.registry.ModEntities;
import com.skilles.chronoclones.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** NeoForge's client registration and render events, routed to the shared client code. */
@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public final class NeoForgeClientBridge {

    private NeoForgeClientBridge() {}

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CHRONO_GHOST.get(), ChronoCloneRenderer::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHRONO_ANCHOR.get(), ChronoAnchorScreen::new);
    }

    @SubscribeEvent
    static void registerHudLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, Chronoclones.id("goggle_truncated"),
                GoggleHud::render);
        event.registerAbove(VanillaGuiLayers.CHAT, Chronoclones.id("recording_overlay"),
                RecordingHud::render);
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        NudgeKeys.forEachMapping(event::register);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        NudgeKeys.tick();
    }

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Foreground event) {
        RecordingHighlights.renderForeground(event.getScreen(), event.getGuiGraphics());
    }

    @SubscribeEvent
    static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        PreviewRenderer.submitGeometry(event.getPoseStack(), event.getSubmitNodeCollector());
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChronoclonesClientInit.disconnected();
    }
}
//?}
