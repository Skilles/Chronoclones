package com.skilles.chronoclones.menu.client;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The pieces the anchor window is drawn out of, so the screen itself is only layout.
 *
 * <p>Nine-sliced sprites rather than one background texture: the rows have moved several times and
 * a baked background would have to be repainted for each move, where these stretch to fit.
 */
final class AnchorPanels {

    private AnchorPanels() {}

    private static final Identifier PANEL_SPRITE = Chronoclones.id("anchor/panel");
    private static final Identifier OUTLINE_SPRITE = Chronoclones.id("anchor/outline");
    private static final Identifier SLOT_SPRITE = Chronoclones.id("anchor/slot");

    static final Identifier ICON_ACTIONS = icon("actions");
    static final Identifier ICON_CLONES = icon("clones");
    static final Identifier ICON_RATE = icon("rate");
    static final Identifier ICON_FUEL = icon("fuel");
    static final Identifier ICON_MODULE = icon("module");
    static final Identifier ICON_WARNING = icon("warning");
    static final Identifier ICON_GEAR = icon("gear");

    static final int ICON_SIZE = 8;

    private static Identifier icon(String name) {
        return Chronoclones.id("anchor/icon/" + name);
    }

    static final int WINDOW = 0xFF1E1E24;
    static final int TEXT = 0xFFE0E0E8;
    static final int MUTED = 0xFF8A8A99;
    static final int ACCENT = 0xFF7FD4C1;
    static final int SLOT_EDGE = 0xFF33333D;

    static final int WARNING = 0xFFE0B860;
    static final int HALTED = 0xFFE06060;

    static final int TRACK = 0xFF15151A;
    static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_SPRITE, x, y, width, height);
    }

    /** A rounded ring in {@code tint}, for whatever needs to be picked out from its neighbours. */
    static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int tint) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, OUTLINE_SPRITE, x, y, width, height, tint);
    }

    /** Drawn around the 16x16 the item occupies, as vanilla's slot texture is. */
    static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x - 1, y - 1, 18, 18);
    }

    static void icon(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int tint) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, ICON_SIZE, ICON_SIZE, tint);
    }

    /**
     * A section name astride its panel's top border, with the border cleared behind it.
     */
    static void legend(GuiGraphicsExtractor g, Font font, String name, int x, int panelTop) {
        int top = panelTop - Layout.LEGEND_RISE;

        g.fill(x - 4, top, x + 6 + font.width(name) + 4, top + font.lineHeight + 1, WINDOW);
        g.fill(x, top + 3, x + 3, top + 6, ACCENT);
        g.text(font, name, x + 6, top, MUTED);
    }

    /** The recessed track a bar runs in. */
    static void track(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, SLOT_EDGE);
        g.fill(x, y, x + width, y + height, TRACK);
    }

    /**
     * A segmented bar filling upwards. Segments rather than one block so a glance reads as a
     * quantity, and upwards because a column of charge reads like a column of charge.
     */
    static void barUp(GuiGraphicsExtractor g, int x, int y, int width, int height,
                      float fraction, int colour) {
        track(g, x, y, width, height);

        int segments = height / 3;
        int lit = Math.round(segments * Math.clamp(fraction, 0f, 1f));
        for (int i = 0; i < lit; i++) {
            int top = y + height - (i + 1) * 3;
            g.fill(x, top, x + width, top + 2, colour);
        }
    }

    /** The playhead: a small diamond sitting astride the timeline. */
    static void playhead(GuiGraphicsExtractor g, int x, int y, int colour) {
        for (int row = 0; row < 4; row++) {
            g.fill(x - row, y + row, x + row + 1, y + row + 1, colour);
            g.fill(x - row, y + 8 - row, x + row + 1, y + 9 - row, colour);
        }
    }
}
