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

    /** Vanilla's experience green, so the anchor's bar and the player's own agree. */
    static final int LEVEL = 0xFF80FF20;

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

    /**
     * A segmented bar filling rightwards, for a quantity read along a row.
     */
    static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height,
                    float fraction, int colour) {
        track(g, x, y, width, height);

        int segments = width / 3;
        int lit = Math.round(segments * Math.clamp(fraction, 0f, 1f));
        for (int i = 0; i < lit; i++) {
            g.fill(x + i * 3, y, x + i * 3 + 2, y + height, colour);
        }
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

    /**
     * A transport control: a box with a glyph in it, lit when it is the state the anchor is in.
     *
     * <p>Drawn rather than blitted, because a triangle, two bars and a square are less trouble as
     * geometry than as three more sprites to keep in step with the rest of the kit.
     */
    static void transport(GuiGraphicsExtractor g, Kind kind, int x, int y, int size,
                          boolean current, boolean hovered) {
        int tint = current ? ACCENT : (hovered ? TEXT : MUTED);

        g.fill(x, y, x + size, y + size, current ? SLOT_EDGE : TRACK);
        outline(g, x, y, size, size, current ? ACCENT : SLOT_EDGE);

        int inset = 4;
        int left = x + inset;
        int top = y + inset;
        int span = size - inset * 2;

        switch (kind) {
            // A triangle, one row at a time, narrowing to a point at the right.
            case PLAY -> {
                for (int row = 0; row < span; row++) {
                    int width = span - Math.abs(row - (span - 1) / 2) * 2;
                    g.fill(left, top + row, left + Math.max(1, width), top + row + 1, tint);
                }
            }
            case PAUSE -> {
                g.fill(left, top, left + 2, top + span, tint);
                g.fill(left + span - 2, top, left + span, top + span, tint);
            }
            case STOP -> g.fill(left, top, left + span, top + span, tint);
        }
    }

    /** The three transport glyphs. */
    enum Kind {
        PLAY,
        PAUSE,
        STOP
    }

    /** A tick mark, drawn inside a box of {@code size}: down to the left, then up to the right. */
    static void tick(GuiGraphicsExtractor g, int x, int y, int size, int colour) {
        int foot = size / 2;
        for (int step = 0; step < foot - 1; step++) {
            g.fill(x + 2 + step, y + foot - 1 + step, x + 3 + step, y + foot + 1 + step, colour);
        }
        for (int step = 0; step < size - foot - 1; step++) {
            g.fill(x + foot + step, y + size - 3 - step, x + foot + 1 + step, y + size - 1 - step,
                    colour);
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
