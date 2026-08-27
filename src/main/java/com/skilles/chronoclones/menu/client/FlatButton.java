package com.skilles.chronoclones.menu.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//? if >=26 {
import net.minecraft.client.input.InputWithModifiers;
//?}
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

class FlatButton extends AbstractButton {

    private final Font font;
    private final Runnable onPress;
    private final boolean danger;

    FlatButton(Font font, int x, int y, int width, int height, Component label, Runnable onPress) {
        this(font, x, y, width, height, label, onPress, false);
    }

    FlatButton(Font font, int x, int y, int width, int height, Component label, Runnable onPress,
               boolean danger) {
        super(x, y, width, height, label);
        this.font = font;
        this.onPress = onPress;
        this.danger = danger;
    }

    //? if >=26 {
    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        onPress.run();
    }
    //?} else {
    /*@Override
    public void onPress() {
        onPress.run();
    }
    *///?}

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        AnchorPanels.panel(g, getX(), getY(), getWidth(), getHeight());

        int accent = danger ? AnchorPanels.HALTED : AnchorPanels.ACCENT;
        if (active && isHoveredOrFocused()) {
            AnchorPanels.outline(g, getX(), getY(), getWidth(), getHeight(), accent);
        }

        int colour = !active ? AnchorPanels.SLOT_EDGE
                : danger ? AnchorPanels.HALTED
                : isHoveredOrFocused() ? accent
                : AnchorPanels.TEXT;

        String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 8);
        g.text(font, label, getX() + (getWidth() - font.width(label)) / 2,
                getY() + (getHeight() - font.lineHeight) / 2 + 1, colour);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
