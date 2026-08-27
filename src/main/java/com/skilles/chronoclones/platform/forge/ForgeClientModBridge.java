//? if forge {
/*package com.skilles.chronoclones.platform.forge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.ChronoCloneRenderer;
import com.skilles.chronoclones.client.ChronoclonesClientInit;
import com.skilles.chronoclones.client.GoggleHud;
import com.skilles.chronoclones.client.NudgeKeys;
import com.skilles.chronoclones.client.RecordingHud;
import com.skilles.chronoclones.menu.client.ChronoAnchorScreen;
import com.skilles.chronoclones.registry.ModEntities;
import com.skilles.chronoclones.registry.ModMenus;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// Forge's client registration events, routed to the shared client code.
@Mod.EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClientModBridge {

    private ForgeClientModBridge() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ChronoclonesClientInit.init();
            MenuScreens.register(ModMenus.CHRONO_ANCHOR.get(), ChronoAnchorScreen::new);
        });
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CHRONO_GHOST.get(), ChronoCloneRenderer::new);
    }

    @SubscribeEvent
    static void registerHudOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("goggle_truncated",
                (gui, graphics, partialTick, width, height) -> GoggleHud.render(graphics, partialTick));
        event.registerAboveAll("recording_overlay",
                (gui, graphics, partialTick, width, height) -> RecordingHud.render(graphics, partialTick));
    }

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        NudgeKeys.forEachMapping(event::register);
    }
}
*///?}
