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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Chronoclones.MODID, value = Dist.CLIENT)
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

    @SubscribeEvent
    static void render(ScreenEvent.Render.Foreground event) {
        Screen screen = event.getScreen();

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        if (containerId >= 0 && containerScreen.getMenu().containerId == containerId) {
            paint(event.getGuiGraphics(), containerScreen, TOUCHED, noAmounts(CARRIED));
            return;
        }

        GoggleSlots.Session session = GoggleSlots.sessionFor(containerScreen);
        if (session != null) {
            paint(event.getGuiGraphics(), containerScreen, session.touched(), session.carried());
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
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, tint);

            ItemStack needed = carried.get(index);
            if (needed == null || needed.isEmpty()) {
                continue;
            }
            needs(graphics, font, slot, needed);
        }
    }

    private static void needs(GuiGraphicsExtractor graphics, Font font, Slot slot, ItemStack needed) {
        if (slot.getItem().isEmpty()) {
            graphics.fakeItem(needed, slot.x, slot.y);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GHOST_VEIL);
        }

        String count = String.valueOf(needed.getCount());
        graphics.text(font, count, slot.x + 17 - font.width(count), slot.y + 9, NEEDS, true);
    }
}
