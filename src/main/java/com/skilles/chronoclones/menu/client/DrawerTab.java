package com.skilles.chronoclones.menu.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

final class DrawerTab extends AbstractButton {

    static final int WIDTH = 15;
    static final int HEIGHT = 20;

    static final int OVERLAP = 3;

    private final Runnable onPress;

    DrawerTab(Component narration, Runnable onPress) {
        super(0, 0, WIDTH, HEIGHT, narration);
        this.onPress = onPress;
    }

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
