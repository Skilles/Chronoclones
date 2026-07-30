package com.skilles.chronoclones.menu.client;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * A yes-or-no control, for the settings that are only ever yes or no.
 *
 * <p>A cycling button can say "Yes" and "Skipped" as readily as a checkbox can, but it cannot say
 * which of them it is offering until you have read the label and worked out that pressing it will
 * change what it says. A box is either ticked or it is not.
 */
class FlatCheckbox extends AbstractButton {

    private final Font font;
    private final BooleanSupplier value;
    private final Consumer<Boolean> onToggle;

    /** Square, and small enough that the label beside it is what the eye reads first. */
    private static final int BOX = 10;

    FlatCheckbox(Font font, int x, int y, int width, int height, Component label,
                 BooleanSupplier value, Consumer<Boolean> onToggle) {
        super(x, y, width, height, label);
        this.font = font;
        this.value = value;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        onToggle.accept(!value.getAsBoolean());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean on = value.getAsBoolean();
        boolean lit = active && isHoveredOrFocused();

        int boxY = getY() + (getHeight() - BOX) / 2;
        AnchorPanels.track(g, getX() + 3, boxY, BOX, BOX);
        if (lit) {
            AnchorPanels.outline(g, getX() + 2, boxY - 1, BOX + 2, BOX + 2, AnchorPanels.ACCENT);
        }

        if (on) {
            AnchorPanels.tick(g, getX() + 3, boxY, BOX,
                    active ? AnchorPanels.ACCENT : AnchorPanels.SLOT_EDGE);
        }

        int colour = !active ? AnchorPanels.SLOT_EDGE : lit ? AnchorPanels.ACCENT : AnchorPanels.TEXT;
        int textX = getX() + 3 + BOX + 5;
        String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - (textX - getX()) - 3);
        g.text(font, label, textX, getY() + (getHeight() - font.lineHeight) / 2 + 1, colour);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
