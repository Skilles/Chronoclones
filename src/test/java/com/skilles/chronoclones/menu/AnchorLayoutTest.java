package com.skilles.chronoclones.menu;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That nothing in the anchor window is drawn on top of anything else.
 */
class AnchorLayoutTest {

    private record Row(String name, int top, int height) {
        int bottom() {
            return top + height;
        }
    }

    /** Every band the screen draws into, top to bottom, in the order it expects them. */
    private static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("title", Layout.TITLE_Y, Layout.LINE_HEIGHT));
        rows.add(new Row("timeline", Layout.TIMELINE_Y, Layout.TIMELINE_HEIGHT));
        rows.add(new Row("pills", Layout.PILLS_Y, Layout.PILLS_HEIGHT));
        rows.add(new Row("storage panel", Layout.STORAGE_PANEL_Y, Layout.STORAGE_PANEL_HEIGHT));
        rows.add(new Row("charge and modules", Layout.MODULE_PANEL_Y, Layout.MODULE_PANEL_HEIGHT));
        rows.add(new Row("diagnostic", Layout.DIAGNOSTIC_Y, Layout.DIAGNOSTIC_HEIGHT));
        rows.add(slots("player inventory", Layout.PLAYER_Y, 3));
        rows.add(slots("hotbar", Layout.HOTBAR_Y, 1));
        return rows;
    }

    /** A band of slot rows, grown by the border the boxes draw outside themselves. */
    private static Row slots(String name, int top, int count) {
        return new Row(name, top - Layout.SLOT_BORDER, count * 18 + Layout.SLOT_BORDER);
    }

    @Test
    @DisplayName("no two rows of the anchor window overlap")
    void rowsDoNotOverlap() {
        List<Row> rows = rows();
        for (int i = 1; i < rows.size(); i++) {
            Row above = rows.get(i - 1);
            Row below = rows.get(i);
            assertTrue(above.bottom() <= below.top(),
                    above.name() + " (ends " + above.bottom() + ") runs into "
                            + below.name() + " (starts " + below.top() + ")");
        }
    }

    @Test
    @DisplayName("every row is inside the window")
    void rowsFitInsideTheWindow() {
        for (Row row : rows()) {
            assertTrue(row.top() >= 0, row.name() + " starts above the window");
            assertTrue(row.bottom() <= Layout.HEIGHT,
                    row.name() + " ends at " + row.bottom() + ", past the window's "
                            + Layout.HEIGHT);
        }
    }

    @Test
    @DisplayName("the storage grid and its header stay inside their panel")
    void storagePanelContainsItsContents() {
        Row panel = new Row("storage panel", Layout.STORAGE_PANEL_Y, Layout.STORAGE_PANEL_HEIGHT);

        assertTrue(Layout.STORAGE_HEADER_Y >= panel.top(), "the header sits above its panel");
        assertTrue(Layout.TAB_Y >= panel.top(), "the clone tabs sit above the panel they belong to");
        assertTrue(Layout.STORAGE_HEADER_Y + Layout.LINE_HEIGHT <= Layout.STORAGE_Y,
                "the header runs into the first row of squares");
        assertTrue(Layout.STORAGE_Y + Layout.STORAGE_ROWS * 18 <= panel.bottom(),
                "the grid runs out of the bottom of its panel");
    }

    @Test
    @DisplayName("the charge readout and the modules stay inside their row")
    void moduleRowContainsItsContents() {
        int top = Layout.MODULE_PANEL_Y;
        int bottom = top + Layout.MODULE_PANEL_HEIGHT;

        assertTrue(Layout.SECTION_LABEL_Y >= top, "the section labels sit above their panels");
        assertTrue(Layout.SECTION_LABEL_Y + Layout.LINE_HEIGHT <= Layout.MODULE_Y,
                "the section labels run into the slot row");
        assertTrue(Layout.MODULE_Y + 18 <= bottom, "the slot row hangs below its panel");
        assertTrue(Layout.CHARGE_Y >= Layout.MODULE_Y, "the charge bar starts above its row");
        assertTrue(Layout.CHARGE_TEXT_Y + Layout.LINE_HEIGHT <= bottom,
                "the charge percentage hangs below its panel");
    }

    @Test
    @DisplayName("the charge and module panels sit side by side without touching")
    void panelsDoNotOverlapHorizontally() {
        assertTrue(Layout.MARGIN + Layout.CHARGE_PANEL_WIDTH < Layout.MODULE_PANEL_X,
                "the charge panel runs into the module panel");
        assertTrue(Layout.CHARGE_X + Layout.CHARGE_WIDTH <= Layout.MARGIN + Layout.CHARGE_PANEL_WIDTH,
                "the charge bar runs out of its panel");
        assertTrue(Layout.UPGRADE_X >= Layout.MODULE_PANEL_X, "the module slots start before their panel");
        assertTrue(Layout.UPGRADE_X + 3 * 18 <= Layout.WIDTH - Layout.MARGIN,
                "the module slots run out of the window");
        assertTrue(Layout.FUEL_X + 16 < Layout.CHARGE_X, "the fuel slot runs into the charge bar");
    }

    @Test
    @DisplayName("the slot grids are centred in the window")
    void gridsAreCentred() {
        int gridWidth = 9 * 18;
        assertEquals(Layout.WIDTH - Layout.GRID_X - gridWidth, Layout.GRID_X,
                "the nine columns are not evenly inset");
    }

    @Test
    @DisplayName("no row is lost on the smallest screen the game will scale to")
    void everyRowSurvivesTheSmallestScreen() {
        // A clone's storage is a whole player inventory, so the window no longer fits the 240px
        // Minecraft guarantees. Vanilla centres it, so the overflow is halved at each edge.
        int clipped = Math.max(0, Layout.HEIGHT - 240) / 2;
        assertTrue(clipped < 18,
                "the window is " + Layout.HEIGHT + " tall, losing " + clipped
                        + "px at each edge, which is a whole slot row");
    }

    @Test
    @DisplayName("a clone's storage is laid out like the player inventory it mirrors")
    void storageMirrorsThePlayerInventory() {
        // The hotbar sits at the bottom of both grids, so a recorded slot lands where the eye
        // expects it rather than nine squares away.
        for (int slot = 0; slot < 9; slot++) {
            assertEquals(3, Layout.storageRow(slot), "hotbar slot " + slot + " is not on the last row");
            assertEquals(slot, Layout.storageColumn(slot));
        }
        assertEquals(0, Layout.storageRow(9));
        assertEquals(2, Layout.storageRow(35));
        assertEquals(8, Layout.storageColumn(35));
    }
}
