package com.skilles.chronoclones.menu.client;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** The handle that opens and closes the precision drawer. Points the way the drawer will move. */
final class DrawerTab extends AbstractButton {

    static final int WIDTH = DrawerLayout.TAB_WIDTH;
    static final int HEIGHT = 14;

    private final Font font;
    private final BooleanSupplier open;
    private final boolean onLeft;
    private final Runnable onPress;

    DrawerTab(Font font, Component narration, boolean onLeft, BooleanSupplier open, Runnable onPress) {
        super(0, 0, WIDTH, HEIGHT, narration);
        this.font = font;
        this.open = open;
        this.onLeft = onLeft;
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        graphics.fill(x, y, x + WIDTH, y + HEIGHT,
                isHoveredOrFocused() ? ChronoAnchorScreen.PANEL_EDGE : ChronoAnchorScreen.SLOT_BG);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, ChronoAnchorScreen.PANEL_BG);

        // Which way it will move, not which side it is on: a chevron that stays put while the drawer
        // slides is telling you where it has been rather than what the button does.
        boolean pointsLeft = open.getAsBoolean() == !onLeft;
        String chevron = pointsLeft ? "<" : ">";
        graphics.text(font, chevron,
                x + (WIDTH - font.width(chevron)) / 2, y + (HEIGHT - font.lineHeight) / 2 + 1,
                ChronoAnchorScreen.ACCENT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
