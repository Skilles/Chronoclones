package com.skilles.chronoclones.menu.client;

import java.util.Optional;

import com.skilles.chronoclones.item.ActionIcons;
import com.skilles.chronoclones.recording.ChronoAction;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Draws what {@link ActionIcons} chose, at whatever size the row it sits in can spare.
 */
final class ActionIcon {

    private ActionIcon() {}

    static boolean draw(GuiGraphicsExtractor g, ChronoAction action, int x, int y, int size) {
        return draw(g, ActionIcons.of(action), x, y, size);
    }

    static boolean draw(GuiGraphicsExtractor g, Optional<Holder<Item>> icon, int x, int y, int size) {
        return icon.filter(held -> draw(g, new ItemStack(held.value()), x, y, size)).isPresent();
    }

    /**
     * Draws an item at {@code size}, scaled from the sixteen pixels items are drawn at.
     *
     * @return false if there is nothing to show, so the caller can fall back to its own mark
     */
    static boolean draw(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return false;
        }

        float scale = size / 16.0f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        // Fake, so a stack of one does not draw a "1" over a mark ten pixels wide.
        g.fakeItem(stack, 0, 0);
        g.pose().popMatrix();
        return true;
    }
}
