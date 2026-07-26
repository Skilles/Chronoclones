package com.skilles.chronoclones.menu.client;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * One checkbox in the anchor's precision drawer.
 *
 * <p>Hand-drawn rather than a vanilla {@code Checkbox}, for the same reason the rest of this screen
 * is: the anchor screen is flat rectangles and text with no texture behind it, and a vanilla widget
 * sprite dropped into the middle of that reads as something pasted on from another mod.
 *
 * <p>A real widget rather than a rectangle and a {@code mouseClicked} branch, though, and that part
 * is not cosmetic. {@code AbstractContainerScreen.mouseClicked} offers the click to its children
 * first and only then decides whether it landed outside the window — where an empty click throws
 * whatever is on the cursor onto the floor. A widget consumes the click; a hand-rolled hit test in
 * the screen would have to be reached before that logic runs, and it is not.
 */
final class PrecisionToggle extends AbstractButton {

    /** A tick, as pixel offsets from the box's top-left. Drawn 2x2 each, so the stroke reads. */
    private static final int[][] TICK = {
            {2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}
    };

    static final int BOX = 12;
    static final int HEIGHT = 12;

    private final Font font;
    private final BooleanSupplier state;
    private final Toggle onToggle;

    interface Toggle {
        void set(boolean selected);
    }

    /**
     * @param state where the current value comes from, read every frame rather than mirrored — the
     *              server owns it, and a widget holding its own copy would keep showing a setting a
     *              refused packet never applied
     */
    PrecisionToggle(Font font, Component label, int width, BooleanSupplier state, Toggle onToggle) {
        super(0, 0, width, HEIGHT, label);
        this.font = font;
        this.state = state;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onToggle.set(!state.getAsBoolean());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean selected = state.getAsBoolean();

        graphics.fill(x, y, x + BOX, y + BOX,
                isHoveredOrFocused() ? ChronoAnchorScreen.PANEL_EDGE : ChronoAnchorScreen.SLOT_BG);
        graphics.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, ChronoAnchorScreen.PANEL_BG);

        if (selected) {
            for (int[] pixel : TICK) {
                graphics.fill(x + pixel[0] - 1, y + pixel[1] - 2,
                        x + pixel[0] + 1, y + pixel[1], ChronoAnchorScreen.ACCENT);
            }
        }

        // Off is muted rather than hidden: which axes are loose is as much of the setting as which
        // are tight, and a row that dims to nothing reads as unavailable instead of unchecked.
        graphics.text(font, getMessage(), x + BOX + 4, y + (BOX - font.lineHeight) / 2 + 1,
                selected ? ChronoAnchorScreen.TEXT : ChronoAnchorScreen.MUTED);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
