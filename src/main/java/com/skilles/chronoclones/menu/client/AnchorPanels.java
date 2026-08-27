package com.skilles.chronoclones.menu.client;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=26 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.resources.Identifier;

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

    static final int LEVEL = 0xFF80FF20;

    static final int TRACK = 0xFF15151A;

    static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        //? if >=26 {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_SPRITE, x, y, width, height);
        //?} else {
        //? if >=1.20.2 {
        /*g.blitSprite(PANEL_SPRITE, x, y, width, height);
        *///?} else {
        /*nineSlice(g, PANEL_SPRITE, x, y, width, height, 4, 16);
        *///?}
        //?}
    }

    static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int tint) {
        //? if >=26 {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, OUTLINE_SPRITE, x, y, width, height, tint);
        //?} else {
        //? if >=1.20.2 {
        /*tinted(g, tint);
        g.blitSprite(OUTLINE_SPRITE, x, y, width, height);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        *///?} else {
        /*tinted(g, tint);
        nineSlice(g, OUTLINE_SPRITE, x, y, width, height, 2, 6);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        *///?}
        //?}
    }

    static void slot(GuiGraphicsExtractor g, int x, int y) {
        //? if >=26 {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x - 1, y - 1, 18, 18);
        //?} else {
        //? if >=1.20.2 {
        /*g.blitSprite(SLOT_SPRITE, x - 1, y - 1, 18, 18);
        *///?} else {
        /*part(g, SLOT_SPRITE, x - 1, y - 1, 18, 18, 0, 0, 18, 18, 18);
        *///?}
        //?}
    }

    static void icon(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int tint) {
        //? if >=26 {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, ICON_SIZE, ICON_SIZE, tint);
        //?} else {
        //? if >=1.20.2 {
        /*tinted(g, tint);
        g.blitSprite(sprite, x, y, ICON_SIZE, ICON_SIZE);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        *///?} else {
        /*tinted(g, tint);
        part(g, sprite, x, y, ICON_SIZE, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        *///?}
        //?}
    }

    //? if <26 {
    /*// The pre-26 sprite blit has no tint parameter; the global colour modulator stands in.
    private static void tinted(GuiGraphicsExtractor g, int tint) {
        g.setColor(((tint >> 16) & 0xFF) / 255.0f, ((tint >> 8) & 0xFF) / 255.0f,
                (tint & 0xFF) / 255.0f, ((tint >>> 24) & 0xFF) / 255.0f);
    }
    *///?}

    //? if <1.20.2 {
    /*// blitSprite and its atlas arrived in 1.20.2; these draw the sprite textures directly,
    // including the nine-slice borders the .mcmeta files would otherwise describe.
    private static void nineSlice(GuiGraphicsExtractor g, Identifier sprite, int x, int y,
                                  int width, int height, int border, int texSize) {
        int inner = texSize - 2 * border;
        part(g, sprite, x, y, border, border, 0, 0, border, border, texSize);
        part(g, sprite, x + width - border, y, border, border, texSize - border, 0, border, border, texSize);
        part(g, sprite, x, y + height - border, border, border, 0, texSize - border, border, border, texSize);
        part(g, sprite, x + width - border, y + height - border, border, border,
                texSize - border, texSize - border, border, border, texSize);
        part(g, sprite, x + border, y, width - 2 * border, border, border, 0, inner, border, texSize);
        part(g, sprite, x + border, y + height - border, width - 2 * border, border,
                border, texSize - border, inner, border, texSize);
        part(g, sprite, x, y + border, border, height - 2 * border, 0, border, border, inner, texSize);
        part(g, sprite, x + width - border, y + border, border, height - 2 * border,
                texSize - border, border, border, inner, texSize);
        part(g, sprite, x + border, y + border, width - 2 * border, height - 2 * border,
                border, border, inner, inner, texSize);
    }

    private static void part(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h,
                             int u, int v, int uw, int vh, int texSize) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.blit(texture(sprite), x, y, w, h, (float) u, (float) v, uw, vh, texSize, texSize);
    }

    private static Identifier texture(Identifier sprite) {
        return new Identifier(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
    }
    *///?}

    static void legend(GuiGraphicsExtractor g, Font font, String name, int x, int panelTop) {
        int top = panelTop - Layout.LEGEND_RISE;

        g.fill(x - 4, top, x + 6 + font.width(name) + 4, top + font.lineHeight + 1, WINDOW);
        g.fill(x, top + 3, x + 3, top + 6, ACCENT);
        g.text(font, name, x + 6, top, MUTED);
    }

    static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height,
                    float fraction, int colour) {
        track(g, x, y, width, height);

        int segments = width / 3;
        int lit = Math.round(segments * Math.clamp(fraction, 0f, 1f));
        for (int i = 0; i < lit; i++) {
            g.fill(x + i * 3, y, x + i * 3 + 2, y + height, colour);
        }
    }

    static void track(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, SLOT_EDGE);
        g.fill(x, y, x + width, y + height, TRACK);
    }

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
            // A dust-diamond, the shape redstone draws on the ground.
            case REDSTONE -> {
                for (int row = 0; row < span; row++) {
                    int width = 2 * (Math.min(row, span - 1 - row) + 1);
                    g.fill(left + (span - width) / 2, top + row,
                            left + (span - width) / 2 + width, top + row + 1, tint);
                }
            }
        }
    }

    enum Kind {

        PLAY,
        PAUSE,
        STOP,
        REDSTONE
    }

    static int wash(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (Math.clamp(alpha, 0, 255) << 24);
    }

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

    static void playhead(GuiGraphicsExtractor g, int x, int y, int colour) {
        for (int row = 0; row < 4; row++) {
            g.fill(x - row, y + row, x + row + 1, y + row + 1, colour);
            g.fill(x - row, y + 8 - row, x + row + 1, y + 9 - row, colour);
        }
    }
}
