package com.skilles.chronoclones.menu.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * A slider in the same clothes as {@link FlatButton}: the value reads as a label, the position as a
 * thin fill along the bottom edge.
 */
abstract class FlatSlider extends AbstractSliderButton {

    private final Font font;

    FlatSlider(Font font, int x, int y, int width, int height, double value) {
        super(x, y, width, height, Component.empty(), value);
        this.font = font;
    }

    @Override
    public void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY,
                                         float partialTick) {
        AnchorPanels.panel(g, getX(), getY(), getWidth(), getHeight());
        if (isHoveredOrFocused()) {
            AnchorPanels.outline(g, getX(), getY(), getWidth(), getHeight(), AnchorPanels.ACCENT);
        }

        int trackX = getX() + 4;
        int trackWidth = getWidth() - 8;
        int trackY = getY() + getHeight() - 5;

        g.fill(trackX, trackY, trackX + trackWidth, trackY + 2, AnchorPanels.TRACK);
        int filled = (int) Math.round(trackWidth * value);
        if (filled > 0) {
            g.fill(trackX, trackY, trackX + filled, trackY + 2, AnchorPanels.ACCENT);
        }
        int handle = trackX + Math.clamp(filled - 1, 0, trackWidth - 2);
        g.fill(handle, trackY - 2, handle + 2, trackY + 4, AnchorPanels.TEXT);

        String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 8);
        g.text(font, label, getX() + (getWidth() - font.width(label)) / 2, getY() + 2,
                isHoveredOrFocused() ? AnchorPanels.ACCENT : AnchorPanels.TEXT);
    }
}
