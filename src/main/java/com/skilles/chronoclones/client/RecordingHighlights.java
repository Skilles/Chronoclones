package com.skilles.chronoclones.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.network.RecordingHighlightPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/**
 * Tints the slots a recording has picked up, over any container opened while recording.
 */
@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
public final class RecordingHighlights {

    private RecordingHighlights() {}

    /** The menu the highlights below belong to; -1 when there is nothing to draw. */
    private static int containerId = -1;
    private static final Set<Integer> TOUCHED = new HashSet<>();
    private static final Set<Integer> CARRIED = new HashSet<>();

    /** Part of the routine. Matches the container session's colour on the recording tooltip. */
    private static final int TOUCHED_TINT = 0x60_FFE14D;
    /** The anchor must supply this. Matches the "brings" lines on the tooltip. */
    private static final int CARRIED_TINT = 0x70_4DE1D0;
    /** The count on a square the anchor has to supply. */
    private static final int NEEDS = 0xFF_FFFFFF;
    /** Knocks a ghosted item back so it reads as an intention rather than as contents. */
    private static final int GHOST_VEIL = 0x88_2B2B33;

    /** From the server. See {@link RecordingHighlightPayload} for why the client is not guessing. */
    public static void accept(RecordingHighlightPayload payload) {
        containerId = payload.containerId();
        TOUCHED.clear();
        TOUCHED.addAll(payload.touched());
        CARRIED.clear();
        CARRIED.addAll(payload.carried());
    }

    /**
     * Leaving a world drops the highlights with it.
     */
    public static void forget() {
        containerId = -1;
        TOUCHED.clear();
        CARRIED.clear();
    }

    @SubscribeEvent
    static void render(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();

        // Scoped to the container it arrived for, so it cannot outlive the menu it describes.
        if (containerId >= 0 && screen.getMenu().containerId == containerId) {
            // A recording in progress has no amounts yet.
            paint(event.getGuiGraphics(), screen, TOUCHED, noAmounts(CARRIED));
            return;
        }

        // Not recording, but the goggles may still know a routine that works this container.
        GoggleSlots.Session session = GoggleSlots.sessionFor(screen);
        if (session != null) {
            paint(event.getGuiGraphics(), screen, session.touched(), session.carried());
        }
    }

    /** Bare slot numbers, for the source that has nothing more to say about them. */
    private static Map<Integer, ItemStack> noAmounts(Set<Integer> slots) {
        Map<Integer, ItemStack> plain = new HashMap<>();
        for (int slot : slots) {
            plain.put(slot, ItemStack.EMPTY);
        }
        return plain;
    }

    /**
     * The drawing, independent of where the slot numbers came from.
     */
    private static void paint(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                              Set<Integer> touched, Map<Integer, ItemStack> carried) {
        Font font = Minecraft.getInstance().font;

        for (int index = 0; index < screen.getMenu().slots.size(); index++) {
            boolean isCarried = carried.containsKey(index);
            int tint = isCarried ? CARRIED_TINT : touched.contains(index) ? TOUCHED_TINT : 0;
            if (tint == 0) {
                continue;
            }

            Slot slot = screen.getMenu().slots.get(index);
            // Fires inside the screen's translation and after the slots, so this lands over them.
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, tint);

            ItemStack needed = carried.get(index);
            if (needed == null || needed.isEmpty()) {
                continue;
            }
            needs(graphics, font, slot, needed);
        }
    }

    /** What one square has to be supplied with, drawn on it. */
    private static void needs(GuiGraphicsExtractor graphics, Font font, Slot slot, ItemStack needed) {
        if (slot.getItem().isEmpty()) {
            // Only over an empty square; over a full one it would read as the contents.
            graphics.fakeItem(needed, slot.x, slot.y);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GHOST_VEIL);
        }

        String count = String.valueOf(needed.getCount());
        // Bottom right, where a stack count always sits, so it reads as one without a legend.
        graphics.text(font, count, slot.x + 17 - font.width(count), slot.y + 9, NEEDS, true);
    }
}
