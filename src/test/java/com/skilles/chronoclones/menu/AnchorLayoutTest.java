package com.skilles.chronoclones.menu;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorLayoutTest {

    private static final int MIN_CANVAS = 240;

    private record Row(String name, int top, int height) {

        int bottom() {
            return top + height;
        }
    }

    private static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("title", Layout.TITLE_Y, Layout.LINE_HEIGHT));
        rows.add(new Row("timeline", Layout.TIMELINE_Y, Layout.TIMELINE_HEIGHT));
        rows.add(new Row("pills", Layout.PILLS_Y, Layout.PILLS_HEIGHT));
        rows.add(new Row("storage band", Layout.BAND_Y, Layout.BAND_HEIGHT));
        rows.add(new Row("inventory panel", Layout.INVENTORY_PANEL_Y, Layout.INVENTORY_PANEL_HEIGHT));
        return rows;
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
    @DisplayName("the player inventory and hotbar stay inside their panel")
    void inventoryPanelContainsItsGrids() {
        int top = Layout.INVENTORY_PANEL_Y;
        int bottom = top + Layout.INVENTORY_PANEL_HEIGHT;

        assertTrue(Layout.PLAYER_Y >= top + Layout.PANEL_INSET,
                "the first inventory row is under the panel's own border");
        assertTrue(Layout.PLAYER_Y + 3 * 18 <= Layout.HOTBAR_Y, "the rows run into the hotbar");
        assertTrue(Layout.HOTBAR_Y + 18 <= bottom - Layout.PANEL_INSET + 1,
                "the hotbar runs out of the bottom of its panel");
        assertTrue(bottom <= Layout.HEIGHT, "the panel runs out of the window");
    }

    @Test
    @DisplayName("the rail and the storage grid are the same four squares tall")
    void bandContainsBothOfItsColumns() {
        int top = Layout.BAND_Y;
        int bottom = top + Layout.BAND_HEIGHT;
        int grid = Layout.STORAGE_ROWS * 18;

        assertEquals(grid, 4 * 18, "the rail no longer matches the grid it stands beside");
        assertTrue(Layout.STORAGE_Y >= top + Layout.PANEL_INSET,
                "the grid is under the panel's own border");
        assertTrue(Layout.STORAGE_Y + grid <= Layout.CLONE_XP_Y,
                "the grid runs into the experience bar beneath it");
        assertTrue(Layout.CLONE_XP_Y + Layout.CLONE_XP_HEIGHT <= bottom - Layout.PANEL_INSET + 1,
                "the experience bar runs out of the bottom of its panel");
        assertTrue(Layout.MODULE_Y + 4 * 18 <= bottom - Layout.PANEL_INSET + 1,
                "the rail's squares run out of the bottom of their panel");
        assertTrue(Layout.CHARGE_Y + Layout.CHARGE_HEIGHT <= bottom - Layout.PANEL_INSET + 1,
                "the charge column runs out of the bottom of its panel");
    }

    @Test
    @DisplayName("the rail stands beside the storage panel rather than over it")
    void railClearsTheStoragePanel() {
        assertTrue(Layout.RAIL_X + Layout.RAIL_WIDTH < Layout.STORAGE_PANEL_X,
                "the rail runs into the storage panel");
        assertTrue(Layout.CHARGE_X + Layout.CHARGE_WIDTH
                        <= Layout.RAIL_X + Layout.RAIL_WIDTH - Layout.PANEL_INSET + 1,
                "the charge column runs out of the rail");
        assertTrue(Layout.FUEL_X + 16 <= Layout.CHARGE_X, "the fuel square runs into the charge column");
        assertTrue(Layout.STORAGE_X + 9 * 18 <= Layout.WIDTH - Layout.MARGIN,
                "the storage grid runs out of the window");
    }

    @Test
    @DisplayName("a section name straddles its panel and still clears the band above")
    void legendsClearTheBandAbove() {
        assertGap("the storage name", Layout.BAND_Y - Layout.LEGEND_RISE,
                Layout.PILLS_Y + Layout.PILLS_HEIGHT);
        assertGap("the inventory name", Layout.INVENTORY_PANEL_Y - Layout.LEGEND_RISE,
                Layout.BAND_Y + Layout.BAND_HEIGHT);
        assertGap("the clone tabs", Layout.TAB_Y, Layout.PILLS_Y + Layout.PILLS_HEIGHT);
    }

    private static void assertGap(String what, int top, int bandAboveBottom) {
        int gap = top - bandAboveBottom;
        assertTrue(gap >= Layout.BAND_GAP - Layout.LEGEND_RISE,
                what + " leaves " + gap + "px above it, which is not clear of the band above");
    }

    @Test
    @DisplayName("the transport controls fit the timeline's row without taking any more of it")
    void transportFitsBesideTheTimeline() {
        assertTrue(Layout.TRANSPORT_Y >= Layout.TITLE_Y + Layout.LINE_HEIGHT,
                "the controls run into the title");
        assertTrue(Layout.TRANSPORT_Y + Layout.TRANSPORT_SIZE <= Layout.PILLS_Y,
                "the controls run into the pills below them");

        assertTrue(Layout.TIMELINE_WIDTH > 0, "the controls left no room for the track");
        assertTrue(Layout.MARGIN + Layout.TIMELINE_WIDTH < Layout.TRANSPORT_X,
                "the track runs into the controls");
        assertEquals(Layout.WIDTH - Layout.MARGIN,
                Layout.transportX(Layout.TRANSPORT_COUNT - 1) + Layout.TRANSPORT_SIZE,
                "the last control is not flush with the window's margin");
    }

    @Test
    @DisplayName("every transport control is clickable and none of them overlap")
    void transportControlsAreSeparate() {
        for (int index = 1; index < Layout.TRANSPORT_COUNT; index++) {
            assertTrue(Layout.transportX(index - 1) + Layout.TRANSPORT_SIZE
                            <= Layout.transportX(index),
                    "control " + index + " overlaps the one before it");
        }
        int clipped = Math.max(0, Layout.HEIGHT - MIN_CANVAS) / 2;
        assertTrue(Layout.TRANSPORT_Y >= clipped,
                "the transport controls are pushed off the top by " + clipped + "px");
    }

    @Test
    @DisplayName("the slot grids are centred in the window")
    void gridsAreCentred() {
        int gridWidth = 9 * 18;
        assertEquals(Layout.WIDTH - Layout.GRID_X - gridWidth, Layout.GRID_X,
                "the nine columns are not evenly inset");
    }

    @Test
    @DisplayName("everything clickable survives the smallest screen the game will scale to")
    void clickableRowsSurviveTheSmallestScreen() {
        int clipped = Math.max(0, Layout.HEIGHT - MIN_CANVAS) / 2;

        assertTrue(Layout.TAB_Y >= clipped,
                "the clone tabs are pushed off the top by " + clipped + "px");
        assertTrue(Layout.STORAGE_Y >= clipped,
                "the first row of storage is pushed off the top by " + clipped + "px");
        assertTrue(Layout.HOTBAR_Y < Layout.HEIGHT - clipped,
                "the hotbar row is pushed off the bottom by " + clipped + "px");
    }

    @Test
    @DisplayName("a clone's storage is laid out like the player inventory it mirrors")
    void storageMirrorsThePlayerInventory() {
        for (int slot = 0; slot < 9; slot++) {
            assertEquals(3, Layout.storageRow(slot), "hotbar slot " + slot + " is not on the last row");
            assertEquals(slot, Layout.storageColumn(slot));
        }
        assertEquals(0, Layout.storageRow(9));
        assertEquals(2, Layout.storageRow(35));
        assertEquals(8, Layout.storageColumn(35));
    }
}
