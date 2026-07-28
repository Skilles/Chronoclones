package com.skilles.chronoclones.menu.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The pieces the anchor window is drawn out of, so the screen itself is only layout.
 *
 * <p>Everything is filled rectangles rather than a background texture: the window is 184 wide and
 * a nine-slice would have to be redrawn every time a row moves.
 */
final class AnchorPanels {

    private AnchorPanels() {}

    static final int WINDOW = 0xFF1E1E24;
    static final int WINDOW_EDGE = 0xFF4A4A5A;
    static final int PANEL = 0xFF26262E;
    static final int PANEL_EDGE = 0xFF3A3A46;
    static final int SLOT = 0xFF141418;
    static final int SLOT_EDGE = 0xFF33333D;

    static final int TEXT = 0xFFE0E0E8;
    static final int MUTED = 0xFF8A8A99;
    static final int ACCENT = 0xFF7FD4C1;

    static final int WARNING = 0xFFE0B860;
    static final int WARNING_FILL = 0xFF3A2C14;
    static final int HALTED = 0xFFE06060;
    static final int HALTED_FILL = 0xFF3A1A1A;
    static final int RUNNING_FILL = 0xFF17302B;

    static final int TRACK = 0xFF15151A;

    /** A bordered box with the corner pixels knocked out, which reads as a rounded edge. */
    static void box(GuiGraphicsExtractor g, int x, int y, int width, int height,
                    int border, int fill) {
        g.fill(x + 1, y, x + width - 1, y + height, border);
        g.fill(x, y + 1, x + width, y + height - 1, border);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        box(g, x, y, width, height, PANEL_EDGE, PANEL);
    }

    static void slot(GuiGraphicsExtractor g, int x, int y) {
        // Drawn around the 16x16 the item occupies, as vanilla's slot texture is.
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT);
    }

    /** A section heading: a small accent square, then the name. */
    static void heading(GuiGraphicsExtractor g, Font font, String name, int x, int y) {
        g.fill(x, y + 3, x + 3, y + 6, ACCENT);
        g.text(font, name, x + 6, y, MUTED);
    }

    /**
     * A segmented bar. Segments rather than one block so a glance reads as a quantity.
     */
    static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height,
                    float fraction, int colour) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_EDGE);
        g.fill(x, y, x + width, y + height, TRACK);

        int segments = width / 3;
        int lit = Math.round(segments * Math.clamp(fraction, 0f, 1f));
        for (int i = 0; i < lit; i++) {
            g.fill(x + i * 3, y, x + i * 3 + 2, y + height, colour);
        }
    }

    /** The warning triangle, wide at the base and hollow in the middle. */
    static void warningIcon(GuiGraphicsExtractor g, int x, int y, int colour, int behind) {
        for (int row = 0; row < 7; row++) {
            g.fill(x + 3 - row / 2, y + row, x + 4 + row / 2, y + row + 1, colour);
        }
        g.fill(x + 3, y + 2, x + 4, y + 4, behind);
        g.fill(x + 3, y + 5, x + 4, y + 6, behind);
    }

    /** A bolt, for the empty fuel slot. */
    static void fuelIcon(GuiGraphicsExtractor g, int x, int y, int colour) {
        g.fill(x + 4, y + 1, x + 7, y + 4, colour);
        g.fill(x + 3, y + 4, x + 6, y + 6, colour);
        g.fill(x + 2, y + 6, x + 5, y + 9, colour);
    }

    /** A socket, for an empty module slot. */
    static void moduleIcon(GuiGraphicsExtractor g, int x, int y, int colour) {
        g.fill(x + 2, y + 1, x + 8, y + 2, colour);
        g.fill(x + 2, y + 8, x + 8, y + 9, colour);
        g.fill(x + 1, y + 2, x + 2, y + 8, colour);
        g.fill(x + 8, y + 2, x + 9, y + 8, colour);
        g.fill(x + 4, y + 4, x + 6, y + 6, colour);
    }

    /** The playhead: a small diamond sitting astride the timeline. */
    static void playhead(GuiGraphicsExtractor g, int x, int y, int colour) {
        for (int row = 0; row < 3; row++) {
            g.fill(x - row, y + row, x + row + 1, y + row + 1, colour);
            g.fill(x - row, y + 6 - row, x + row + 1, y + 7 - row, colour);
        }
    }
}
