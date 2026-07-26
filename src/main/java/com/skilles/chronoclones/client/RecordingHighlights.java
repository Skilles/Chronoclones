package com.skilles.chronoclones.client;

import java.util.HashSet;
import java.util.Set;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.network.RecordingHighlightPayload;

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
        if (containerId < 0 || screen.getMenu().containerId != containerId) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        for (int index = 0; index < screen.getMenu().slots.size(); index++) {
            int tint = CARRIED.contains(index) ? CARRIED_TINT
                    : TOUCHED.contains(index) ? TOUCHED_TINT
                    : 0;
            if (tint == 0) {
                continue;
            }
            Slot slot = screen.getMenu().slots.get(index);
            // The event fires inside the screen's own translation, so slot coordinates are already
            // the right ones, and after the slots are drawn, so this lands over the item.
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, tint);
        }
    }
}
