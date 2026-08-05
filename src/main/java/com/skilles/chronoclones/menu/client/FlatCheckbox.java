package com.skilles.chronoclones.menu.client;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//? if >=26 {
import net.minecraft.client.input.InputWithModifiers;
//?}
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

class FlatCheckbox extends AbstractButton {

    private final Font font;
    private final BooleanSupplier value;
    private final Consumer<Boolean> onToggle;

    private static final int BOX = 10;

    FlatCheckbox(Font font, int x, int y, int width, int height, Component label,
                 BooleanSupplier value, Consumer<Boolean> onToggle) {
        super(x, y, width, height, label);
        this.font = font;
        this.value = value;
        this.onToggle = onToggle;
    }

    //? if >=26 {
    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        onToggle.accept(!value.getAsBoolean());
    }
    //?} else {
    /*@Override
    public void onPress() {
        onToggle.accept(!value.getAsBoolean());
    }
    *///?}

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
