package com.skilles.chronoclones.client;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.registry.ModDataComponents;
import com.skilles.chronoclones.registry.RecordingProgress;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;

/**
 * The overlay drawn over everything while a recording is running.
 */
@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public final class RecordingHud {

    private RecordingHud() {}

    private static final RecordingHudState STATE = new RecordingHudState();

    /** Recording red, and the amber the border turns when something needs reading. */
    private static final int LIVE = 0xFF4B4B;
    private static final int WARN = 0xFFB020;

    /** Thickness of the border in scaled pixels, drawn as this many one-pixel rings. */
    private static final int BORDER = 18;

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, Chronoclones.id("recording_overlay"), RecordingHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gui.hud.isHidden()) {
            return;
        }

        RecordingProgress progress = stampOf(player);
        long now = client.level.getGameTime();
        boolean live = STATE.update(
                progress == null ? null : progress.sessionId(),
                progress == null ? 0 : progress.elapsedTicks(),
                progress == null ? 0 : progress.actionCount(),
                progress != null && progress.outOfRangeWarning(),
                now);
        if (!live) {
            return;
        }

        boolean warning = STATE.isWarning(now);
        // Partial ticks rather than whole ones, or it strobes.
        float phase = (now + delta.getGameTimeDeltaPartialTick(false)) / 9.0f;
        float pulse = warning ? 1.0f : 0.62f + 0.38f * (float) ((Math.sin(phase) + 1.0) / 2.0);

        border(graphics, warning ? WARN : LIVE, pulse);
        readout(graphics, client, warning);
    }

    /** A vignette rather than a hard frame: hard edges read as a broken texture. */
    private static void border(GuiGraphicsExtractor graphics, int colour, float pulse) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        for (int ring = 0; ring < BORDER; ring++) {
            // Quadratic falloff: dense at the edge, gone before anything readable.
            float fromEdge = 1.0f - ring / (float) BORDER;
            int alpha = (int) (0x66 * fromEdge * fromEdge * pulse);
            if (alpha <= 0) {
                continue;
            }
            int tint = alpha << 24 | colour;
            graphics.fill(ring, ring, width - ring, ring + 1, tint);
            graphics.fill(ring, height - ring - 1, width - ring, height - ring, tint);
            graphics.fill(ring, ring + 1, ring + 1, height - ring - 1, tint);
            graphics.fill(width - ring - 1, ring + 1, width - ring, height - ring - 1, tint);
        }
    }

    /** Time, actions, and how close each is to the cap that will stop the session. */
    private static void readout(GuiGraphicsExtractor graphics, Minecraft client, boolean warning) {
        int centre = graphics.guiWidth() / 2;
        int top = 6;

        graphics.centeredText(client.font, Component.translatable("hud.chronoclones.recording",
                        RecordingHudState.clock(STATE.elapsedTicks()), STATE.actionCount())
                .withStyle(warning ? ChatFormatting.GOLD : ChatFormatting.RED), centre, top, 0xFFFFFFFF);

        // Both caps, side by side, because either one ending the session is a surprise otherwise.
        int barWidth = 60;
        int gap = 4;
        int barY = top + 12;
        meter(graphics, centre - barWidth - gap / 2, barY, barWidth,
                RecordingHudState.fraction(STATE.elapsedTicks(), ChronoclonesConfig.MAX_RECORDING_TICKS.getAsInt()));
        meter(graphics, centre + gap / 2, barY, barWidth,
                RecordingHudState.fraction(STATE.actionCount(), ChronoclonesConfig.MAX_ACTIONS.getAsInt()));

        if (warning) {
            graphics.centeredText(client.font,
                    Component.translatable("message.chronoclones.recorder.out_of_range")
                            .withStyle(ChatFormatting.GOLD),
                    centre, barY + 8, 0xFFFFFFFF);
        }
    }

    private static void meter(GuiGraphicsExtractor graphics, int x, int y, int width, float filled) {
        graphics.fill(x, y, x + width, y + 3, 0x80_000000);
        // The colour is the warning: a bar that only changes length gets read as decoration.
        int colour = filled > 0.9f ? 0xFF_FF4B4B : filled > 0.75f ? 0xFF_FFB020 : 0xFF_9BE8A0;
        graphics.fill(x, y, x + Math.round(width * filled), y + 3, colour);
    }

    /**
     * The progress stamp on whichever recorder in the inventory carries one.
     */
    private static @Nullable RecordingProgress stampOf(LocalPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            RecordingProgress progress = stack.get(ModDataComponents.PROGRESS.get());
            if (progress != null) {
                return progress;
            }
        }
        return null;
    }
}
