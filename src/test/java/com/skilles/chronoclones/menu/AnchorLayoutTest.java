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
        // The title uses vanilla's titleLabelY, which is 6 and not ours to set.
        rows.add(new Row("title", 6, Layout.LINE_HEIGHT));
        rows.add(new Row("progress", Layout.STATUS_Y, Layout.LINE_HEIGHT));
        rows.add(new Row("upgrades", Layout.UPGRADE_INFO_Y, Layout.LINE_HEIGHT));
        rows.add(slots("storage", Layout.STORAGE_Y, 4));
        rows.add(new Row("section labels", Layout.SECTION_LABEL_Y, Layout.LINE_HEIGHT));
        rows.add(slots("fuel, charge and modules", Layout.MODULE_Y, 1));
        rows.add(new Row("diagnostic", Layout.DIAGNOSTIC_Y, Layout.LINE_HEIGHT));
        rows.add(new Row("inventory label", Layout.PLAYER_LABEL_Y, Layout.LINE_HEIGHT));
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
    @DisplayName("the charge bar sits inside the module row rather than beside it")
    void chargeBarIsInTheModuleRow() {
        // It shares the row with the fuel slot and the module slots, so it is the one element whose
        // vertical placement is a containment rather than a gap.
        assertTrue(Layout.CHARGE_Y >= Layout.MODULE_Y,
                "the charge bar starts above the row it belongs to");
        assertTrue(Layout.CHARGE_Y + Layout.CHARGE_HEIGHT <= Layout.MODULE_Y + 18,
                "the charge bar hangs below the row it belongs to");
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
