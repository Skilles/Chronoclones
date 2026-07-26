package com.skilles.chronoclones.menu;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That nothing in the anchor window is drawn on top of anything else.
 *
 * <p>Written after two overlaps shipped, and both are worth naming because they share a shape. The
 * origin readout was tucked ten pixels above the status line — which is where the title is — so it
 * appeared directly through the window's own name, but only once a routine had been nudged. And the
 * matching text sat on the end of the upgrades line, where it fitted until the wording got longer
 * and then ran off the edge of the window entirely.
 *
 * <p>Neither showed up while writing the code. Both are one comparison here.
 *
 * <p>Vertical only: horizontal placement depends on {@code Font.width}, which needs a client. What
 * this can check is that the rows are in order and that they all fit inside the window, which is
 * where the mistakes actually were.
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
        rows.add(new Row("matching and origin", Layout.MATCHING_Y, Layout.LINE_HEIGHT));
        rows.add(slots("storage", Layout.STORAGE_Y, 2));
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
    @DisplayName("the window fits the smallest screen the game will scale to")
    void windowFitsTheSmallestScreen() {
        // Minecraft clamps the GUI scale so the effective viewport stays at least 320x240. A window
        // taller than that cannot be fully seen at any scale, and the readouts at the bottom are the
        // ones that would go — the diagnostic line included.
        assertTrue(Layout.HEIGHT <= 240,
                "the window is " + Layout.HEIGHT + " tall and would be clipped at every GUI scale");
    }
}
