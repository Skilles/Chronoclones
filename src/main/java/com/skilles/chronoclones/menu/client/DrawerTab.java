package com.skilles.chronoclones.menu.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * The handle on the anchor window's edge that opens the routine editor.
 *
 * <p>It used to pull out a drawer, which existed to hold one button that opened the editor. The
 * handle does that itself now.
 */
final class DrawerTab extends AbstractButton {

    static final int WIDTH = 15;
    static final int HEIGHT = 20;

    /**
     * How far the handle tucks under the window's edge, so it reads as growing out of the window
     * rather than as a button parked against it.
     */
    static final int OVERLAP = 3;

    private final Runnable onPress;

    DrawerTab(Component narration, Runnable onPress) {
        super(0, 0, WIDTH, HEIGHT, narration);
        this.onPress = onPress;
    }

    /** Always the right edge: with no body to make room for, there is no side to choose. */
    static int tabX(int leftPos, int imageWidth) {
        return leftPos + imageWidth - OVERLAP;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();

        AnchorPanels.panel(g, x, y, WIDTH, HEIGHT);

        int colour = !active ? AnchorPanels.SLOT_EDGE
                : isHovered() ? AnchorPanels.ACCENT
                : AnchorPanels.MUTED;

        // The gear centres on the part that is not tucked under the window.
        float halfOverlap = (float) OVERLAP / 2;
        float xFloat = x + halfOverlap + (WIDTH - halfOverlap - AnchorPanels.ICON_SIZE) / 2;
        AnchorPanels.icon(g, AnchorPanels.ICON_GEAR,
                (int) xFloat,
                y + (HEIGHT - AnchorPanels.ICON_SIZE) / 2, colour);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
