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
 *
 * <p>A container session is the one part of a routine you cannot see yourself performing. Breaking a
 * block leaves a hole; clicking slot 31 instead of slot 30 looks identical, and the mistake surfaces
 * an hour later as a clone that shuffles items into the wrong square. This is the feedback the rest
 * of recording already has.
 *
 * <p>Two colours, because the two facts have different consequences. Yellow is "this square is part
 * of the routine". Aqua is "the anchor will have to be stocked with what is in here" — the tint
 * matching the colour those items get on the shard's tooltip, so the same fact reads the same way in
 * both places.
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
     *
     * <p>Container ids are small integers a server hands out from zero, so a highlight kept across a
     * disconnect will eventually match a completely unrelated menu on the next world — and paint
     * squares in it that no recording ever touched.
     */
    public static void forget() {
        containerId = -1;
        TOUCHED.clear();
        CARRIED.clear();
    }

    @SubscribeEvent
    static void render(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();

        // A highlight that outlives the menu it describes would be pointing at the wrong squares, so
        // it is scoped to the container it arrived for rather than cleared on some close event.
        if (containerId >= 0 && screen.getMenu().containerId == containerId) {
            // A recording in progress has no amounts to show: what it will need is whatever the
            // player is holding when they close the container, which has not happened yet.
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
     *
     * <p>Two sources feed this: a live recording pushed from the server, and a routine the goggles
     * already know about. They answer the same question — which squares does this task touch — so
     * they had better look identical, and the only way to guarantee that is one painter.
     *
     * <h2>What the marks mean</h2>
     *
     * <p>The fill says a routine works this square. A square the anchor has to <em>supply</em> gets
     * the item and the amount as well, because that is a requirement rather than an observation: the
     * routine stages exactly what it recorded, and a square it cannot fill stops the session. Reading
     * that off the squares is how you know what to put in the anchor.
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
            // The event fires inside the screen's own translation, so slot coordinates are already
            // the right ones, and after the slots are drawn, so this lands over the item.
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
            // Only over an empty square. Drawn over an occupied one it would read as the item that
            // is there, which is exactly the thing it exists to distinguish itself from.
            graphics.fakeItem(needed, slot.x, slot.y);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GHOST_VEIL);
        }

        String count = String.valueOf(needed.getCount());
        // Bottom right, where a stack count always sits, so it reads as one without a legend.
        graphics.text(font, count, slot.x + 17 - font.width(count), slot.y + 9, NEEDS, true);
    }
}
