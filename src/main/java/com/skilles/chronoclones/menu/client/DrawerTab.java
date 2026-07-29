package com.skilles.chronoclones.menu.client;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** The handle that opens and closes the precision drawer. Points the way the drawer will move. */
final class DrawerTab extends AbstractButton {

    static final int WIDTH = DrawerLayout.TAB_WIDTH;
    static final int HEIGHT = 20;

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
    public void onPress(@NonNull InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        // Lit means open, not focused: a clicked button keeps focus, so the drawer stayed lit
        // after it had slid shut.
        boolean lit = open.getAsBoolean() || isHovered();

        AnchorPanels.panel(graphics, x, y, WIDTH, HEIGHT);

        // The gear centres on the part that is not tucked under the window.
        int visible = WIDTH - DrawerLayout.TAB_OVERLAP;
        int iconX = onLeft
                ? x + (visible - AnchorPanels.ICON_SIZE) / 2
                : x + DrawerLayout.TAB_OVERLAP + (visible - AnchorPanels.ICON_SIZE) / 2;

        AnchorPanels.icon(graphics, AnchorPanels.ICON_GEAR,
                iconX, y + (HEIGHT - AnchorPanels.ICON_SIZE) / 2,
                lit ? AnchorPanels.ACCENT : AnchorPanels.MUTED);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
