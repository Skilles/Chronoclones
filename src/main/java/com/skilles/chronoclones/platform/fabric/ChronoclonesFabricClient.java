//? if fabric {
/*package com.skilles.chronoclones.platform.fabric;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.ChronoCloneRenderer;
import com.skilles.chronoclones.client.ChronoclonesClientInit;
import com.skilles.chronoclones.client.GoggleHud;
import com.skilles.chronoclones.client.NudgeKeys;
import com.skilles.chronoclones.client.RecordingHighlights;
import com.skilles.chronoclones.client.RecordingHud;
import com.skilles.chronoclones.client.preview.PreviewRenderer;
import com.skilles.chronoclones.menu.client.ChronoAnchorScreen;
import com.skilles.chronoclones.network.ChronoclonesNetwork;
import com.skilles.chronoclones.registry.ModEntities;
import com.skilles.chronoclones.registry.ModMenus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class ChronoclonesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ChronoclonesClientInit.init();

        ChronoclonesNetwork.toClient().forEach(ChronoclonesFabricClient::registerToClient);

        MenuScreens.register(ModMenus.CHRONO_ANCHOR.get(), ChronoAnchorScreen::new);
        EntityRendererRegistry.register(ModEntities.CHRONO_GHOST.get(), ChronoCloneRenderer::new);

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Chronoclones.id("goggle_truncated"), GoggleHud::render);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Chronoclones.id("recording_overlay"), RecordingHud::render);

        NudgeKeys.forEachMapping(KeyMappingHelper::registerKeyMapping);
        ClientTickEvents.END_CLIENT_TICK.register(client -> NudgeKeys.tick());

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterExtract(screen).register((rendered, graphics, mouseX, mouseY, delta) ->
                        RecordingHighlights.renderForeground(rendered, graphics)));

        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
                PreviewRenderer.submitGeometry(context.poseStack(), context.submitNodeCollector()));

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ChronoclonesClientInit.disconnected());
    }

    private static <T extends CustomPacketPayload> void registerToClient(
            ChronoclonesNetwork.ToClient<T> entry) {
        ClientPlayNetworking.registerGlobalReceiver(entry.type(),
                (payload, context) -> entry.handler().accept(payload));
    }
}
*///?}
