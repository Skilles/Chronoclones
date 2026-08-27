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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
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

        registerHudAndKeys();
        ClientTickEvents.END_CLIENT_TICK.register(client -> NudgeKeys.tick());
        registerScreenHighlights();
        registerWorldPreview();

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ChronoclonesClientInit.disconnected());
    }
*///?}
//? if fabric {
//? if >=26 {
/*    private static void registerHudAndKeys() {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
                Chronoclones.id("goggle_truncated"), GoggleHud::render);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
                Chronoclones.id("recording_overlay"), RecordingHud::render);
        NudgeKeys.forEachMapping(
                net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper::registerKeyMapping);
    }

    private static void registerScreenHighlights() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterExtract(screen).register((rendered, graphics, mouseX, mouseY, delta) ->
                        RecordingHighlights.renderForeground(rendered, graphics)));
    }

    private static void registerWorldPreview() {
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(
                context -> PreviewRenderer.submitGeometry(
                        context.poseStack(), context.submitNodeCollector()));
    }
*///?} else {
/*    private static void registerHudAndKeys() {
        // The layered HUD registry is a 26.x shape; one callback draws both overlays here.
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                (graphics, delta) -> {
                    GoggleHud.render(graphics, delta);
                    RecordingHud.render(graphics, delta);
                });
        NudgeKeys.forEachMapping(
                net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper::registerKeyBinding);
    }

    private static void registerScreenHighlights() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterRender(screen).register((rendered, graphics, mouseX, mouseY, delta) ->
                        RecordingHighlights.renderForeground(rendered, graphics)));
    }

    private static void registerWorldPreview() {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(
                context -> {
                    var consumers = context.consumers();
                    if (consumers != null) {
                        PreviewRenderer.renderGeometry(context.matrixStack(), consumers);
                    }
                });
    }
*///?}
//?}
//? if fabric {
/*    private static <T extends CustomPacketPayload> void registerToClient(
            ChronoclonesNetwork.ToClient<T> entry) {
*///?}
//? if fabric {
//? if >=1.20.5 {
/*        ClientPlayNetworking.registerGlobalReceiver(entry.type(),
                (payload, context) -> entry.handler().accept(payload));
*///?} else {
/*        ClientPlayNetworking.registerGlobalReceiver(entry.type().id(),
                (client, handler, buf, sender) -> {
                    T payload = entry.codec().decode(
                            new com.skilles.chronoclones.compat.RegistryFriendlyByteBuf(
                                    buf, handler.registryAccess()));
                    client.execute(() -> entry.handler().accept(payload));
                });
*///?}
//?}
//? if fabric {
/*    }
}
*///?}
