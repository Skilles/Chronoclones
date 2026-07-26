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
    /** An axis the anchor is specific about: the border, and the count. */
    private static final int PINNED = 0xFF_FFFFFF;
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
            // A recording in progress has no precision to show: the flags belong to whichever anchor
            // the routine eventually lands on, and it has not landed anywhere yet.
            paint(event.getGuiGraphics(), screen, TOUCHED, expectations(CARRIED));
            return;
        }

        // Not recording, but the goggles may still know a routine that works this container.
        GoggleSlots.Session session = GoggleSlots.sessionFor(screen);
        if (session != null) {
            paint(event.getGuiGraphics(), screen, session.touched(), session.carried());
        }
    }

    /** Bare slot numbers, for the source that has nothing more to say about them. */
    private static Map<Integer, GoggleSlots.Expect> expectations(Set<Integer> slots) {
        Map<Integer, GoggleSlots.Expect> plain = new HashMap<>();
        for (int slot : slots) {
            plain.put(slot, null);
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
     * <p>The fill is unconditional and means only "this routine works here". The three axes each add
     * their own mark on top, and they are independent: an anchor specific about items but not about
     * squares still tells you which item, because the squares it stocks are named by the recording
     * and staged directly rather than chosen at click time.
     *
     * <p>So the strictness of an anchor is legible from how much decoration a square carries, which
     * is the thing you actually want to know at a glance and the thing the anchor screen can only
     * tell you one anchor at a time.
     */
    private static void paint(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen,
                              Set<Integer> touched, Map<Integer, GoggleSlots.Expect> carried) {
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

            GoggleSlots.Expect expect = carried.get(index);
            if (expect == null) {
                continue;
            }
            marks(graphics, font, slot, expect);
        }
    }

    /** The per-axis decoration on one stocked square. */
    private static void marks(GuiGraphicsExtractor graphics, Font font, Slot slot,
                              GoggleSlots.Expect expect) {
        if (expect.precision().slot()) {
            border(graphics, slot.x, slot.y);
        }

        if (expect.precision().item() && slot.getItem().isEmpty()) {
            // Only over an empty square. Drawn over an occupied one it would read as the item that
            // is there, which is exactly the thing the mark exists to distinguish it from.
            graphics.fakeItem(expect.stack(), slot.x, slot.y);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GHOST_VEIL);
        }

        if (expect.precision().quantity()) {
            String count = String.valueOf(expect.stack().getCount());
            // Bottom right, where a stack count always sits, so it reads as one without a legend.
            graphics.text(font, count, slot.x + 17 - font.width(count), slot.y + 9, PINNED, true);
        }
    }

    /** A hard edge inside the square: this one, not one like it. */
    private static void border(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 16, y + 1, PINNED);
        graphics.fill(x, y + 15, x + 16, y + 16, PINNED);
        graphics.fill(x, y + 1, x + 1, y + 15, PINNED);
        graphics.fill(x + 15, y + 1, x + 16, y + 15, PINNED);
    }
}
