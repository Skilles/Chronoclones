package com.skilles.chronoclones.client;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.preview.GoggleCache;
import com.skilles.chronoclones.client.preview.PreviewCache;
import com.skilles.chronoclones.item.RecordingTooltips;
import com.skilles.chronoclones.menu.client.ChronoAnchorScreen;
import com.skilles.chronoclones.network.ChronoclonesNetwork;
import com.skilles.chronoclones.registry.ModEntities;
import com.skilles.chronoclones.registry.ModMenus;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import com.skilles.chronoclones.menu.client.RoutineEditorScreen;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Chronoclones.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public class ChronoclonesClient {

    public ChronoclonesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        ChronoclonesNetwork.clientReplyHandler = PreviewCache::accept;
        ChronoclonesNetwork.clientHighlightHandler = RecordingHighlights::accept;
        ChronoclonesNetwork.clientGoggleHandler = GoggleCache::accept;
        ChronoclonesNetwork.clientRoutineHandler = ChronoclonesClient::openRoutineEditor;
        ChronoclonesNetwork.clientSkinHandler = AuthorSkins::accept;
        RecordingTooltips.detailRequested = () -> {
            var window = Minecraft.getInstance().getWindow();
            return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        };
    }

    private static void openRoutineEditor(com.skilles.chronoclones.network.RoutinePayloads.Open open) {
        Minecraft.getInstance().setScreenAndShow(
                new RoutineEditorScreen(open.source(), open.recording()));
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CHRONO_GHOST.get(), ChronoCloneRenderer::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHRONO_ANCHOR.get(), ChronoAnchorScreen::new);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PreviewCache.forget();
        GoggleCache.forget();
        RecordingHighlights.forget();
        AuthorSkins.forget();
    }
}
