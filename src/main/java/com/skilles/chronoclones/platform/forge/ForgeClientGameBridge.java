//? if forge {
/*package com.skilles.chronoclones.platform.forge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.client.ChronoclonesClientInit;
import com.skilles.chronoclones.client.NudgeKeys;
import com.skilles.chronoclones.client.RecordingHighlights;
import com.skilles.chronoclones.client.preview.PreviewRenderer;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Forge's client render and tick events, routed to the shared client code.
@Mod.EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public final class ForgeClientGameBridge {

    private ForgeClientGameBridge() {}

    @SubscribeEvent
    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            NudgeKeys.tick();
        }
    }

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        RecordingHighlights.renderForeground(event.getScreen(), event.getGuiGraphics());
    }

    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            return;
        }
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        PreviewRenderer.renderGeometry(event.getPoseStack(), buffers);
        buffers.endBatch(net.minecraft.client.renderer.RenderType.lines());
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChronoclonesClientInit.disconnected();
    }
}
*///?}
