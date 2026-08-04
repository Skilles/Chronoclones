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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public final class RecordingHighlights {

    private RecordingHighlights() {}

    private static int containerId = -1;
    private static final Set<Integer> TOUCHED = new HashSet<>();
    private static final Set<Integer> CARRIED = new HashSet<>();

    private static final int TOUCHED_TINT = 0x60_FFE14D;
    private static final int CARRIED_TINT = 0x70_4DE1D0;
    private static final int NEEDS = 0xFF_FFFFFF;
    private static final int GHOST_VEIL = 0x88_2B2B33;

    public static void accept(RecordingHighlightPayload payload) {
        containerId = payload.containerId();
        TOUCHED.clear();
        TOUCHED.addAll(payload.touched());
        CARRIED.clear();
        CARRIED.addAll(payload.carried());
    }

    public static void forget() {
        containerId = -1;
        TOUCHED.clear();
        CARRIED.clear();
    }

    /** Painted over every screen's foreground by the loader's screen-render hook. */
    public static void renderForeground(Screen screen, GuiGraphicsExtractor graphics) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        if (containerId >= 0 && containerScreen.getMenu().containerId == containerId) {
            paint(graphics, containerScreen, TOUCHED, noAmounts(CARRIED));
            return;
        }

        GoggleSlots.Session session = GoggleSlots.sessionFor(containerScreen);
        if (session != null) {
            paint(graphics, containerScreen, session.touched(), session.carried());
        }
    }

    private static Map<Integer, ItemStack> noAmounts(Set<Integer> slots) {
        Map<Integer, ItemStack> plain = new HashMap<>();
        for (int slot : slots) {
            plain.put(slot, ItemStack.EMPTY);
        }
        return plain;
    }

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
            var slotX = screen.leftPos + slot.x;
            var slotY = screen.topPos + slot.y;
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, tint);

            ItemStack needed = carried.get(index);
            if (needed == null || needed.isEmpty()) {
                continue;
            }
            needs(graphics, screen, font, slot, needed);
        }
    }

    private static void needs(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Font font, Slot slot, ItemStack needed) {
        var slotX = screen.leftPos + slot.x;
        var slotY = screen.topPos + slot.y;
        if (slot.getItem().isEmpty()) {
            graphics.fakeItem(needed, slotX, slotY);
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, GHOST_VEIL);
        }

        String count = String.valueOf(needed.getCount());
        graphics.text(font, count, slotX + 17 - font.width(count), slotY + 9, NEEDS, true);
    }
}
