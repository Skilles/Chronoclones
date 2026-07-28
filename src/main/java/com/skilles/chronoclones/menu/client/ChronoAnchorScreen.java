package com.skilles.chronoclones.menu.client;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;

/**
 * The Chrono Anchor screen: a timeline, the running totals, one clone's storage, charge and
 * modules, the diagnostic, and the player's own inventory.
 */
public class ChronoAnchorScreen extends AbstractContainerScreen<ChronoAnchorMenu> {

    /** A quarter per tick: five frames of movement, enough to read as a drawer and not as a stutter. */
    private static final float DRAWER_STEP = 0.25f;
    private static final int DRAWER_TAB_Y = 16;
    private static final int DRAWER_PADDING = 6;
    private static final int DRAWER_TITLE_Y = 6;
    private static final int DRAWER_HEIGHT = 70;

    /** How far the drawer is open, 0 to 1. Advanced a step per tick, so it slides rather than snaps. */
    private float openness;
    private float previousOpenness;
    private boolean drawerOpen;
    private boolean drawerOnLeft;

    public ChronoAnchorScreen(ChronoAnchorMenu menu, Inventory playerInventory, Component title) {
        // imageWidth/imageHeight are final in 26.x and must go through the 5-arg constructor.
        super(menu, playerInventory, title, Layout.WIDTH, Layout.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        drawerOnLeft = DrawerLayout.opensLeft(width, leftPos, imageWidth);

        addRenderableWidget(new DrawerTab(font,
                Component.translatable("gui.chronoclones.anchor.settings"),
                drawerOnLeft, () -> drawerOpen, () -> drawerOpen = !drawerOpen))
                .setPosition(DrawerLayout.tabX(drawerOnLeft, leftPos, imageWidth),
                        topPos + DRAWER_TAB_Y);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        previousOpenness = openness;
        openness = Math.clamp(openness + (drawerOpen ? DRAWER_STEP : -DRAWER_STEP), 0f, 1f);
    }

    /** Not inside the container's pose translation, so coordinates here are absolute. */
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(extractor, mouseX, mouseY, partialTick);

        int xo = leftPos;
        int yo = topPos;

        // Before the window, so a drawer still sliding out passes behind it rather than over it.
        drawer(extractor, xo, yo, partialTick);

        AnchorPanels.box(extractor, xo - 1, yo - 1, imageWidth + 2, imageHeight + 2,
                AnchorPanels.WINDOW_EDGE, AnchorPanels.WINDOW);

        timeline(extractor, xo, yo);
        pills(extractor, xo, yo);
        storagePanel(extractor, xo, yo);
        chargePanel(extractor, xo, yo);
        modulePanel(extractor, xo, yo);
        diagnosticBanner(extractor, xo, yo);
        playerInventory(extractor, xo, yo);
    }

    // ------------------------------------------------------------------ sections

    /**
     * The routine's length, ticked where its actions fall, with a diamond per clone.
     */
    private void timeline(GuiGraphicsExtractor extractor, int xo, int yo) {
        int x = xo + Layout.MARGIN;
        int y = yo + Layout.TIMELINE_Y;
        int width = Layout.CONTENT_WIDTH;
        int height = Layout.TIMELINE_HEIGHT;

        AnchorPanels.box(extractor, x, y, width, height,
                AnchorPanels.PANEL_EDGE, AnchorPanels.TRACK);

        int length = menu.getLengthTicks();
        if (length <= 0) {
            return;
        }

        for (int tick : menu.getActionTicks()) {
            int at = x + 1 + (width - 2) * Math.clamp(tick, 0, length) / length;
            extractor.fill(at, y + 1, at + 1, y + height - 1, AnchorPanels.ACCENT);
        }

        for (int clone = 0; clone < menu.getActiveClones(); clone++) {
            int playhead = Math.clamp(menu.getPlayhead(clone), 0, length);
            int at = x + 1 + (width - 2) * playhead / length;
            AnchorPanels.playhead(extractor, at, y - 1, AnchorPanels.TEXT);
        }
    }

    /**
     * Actions, clones and rate, each in its own box, sized to what it says.
     */
    private void pills(GuiGraphicsExtractor extractor, int xo, int yo) {
        int x = xo + Layout.MARGIN;
        int y = yo + Layout.PILLS_Y;

        for (Pill pill : pillContents()) {
            int width = pillWidth(pill);
            AnchorPanels.panel(extractor, x, y, width, Layout.PILLS_HEIGHT);

            int textY = y + (Layout.PILLS_HEIGHT - font.lineHeight) / 2 + 1;
            extractor.text(font, pill.value(), x + PILL_PADDING, textY, AnchorPanels.ACCENT);
            extractor.text(font, pill.label(),
                    x + PILL_PADDING + font.width(pill.value()) + PILL_SPACING, textY,
                    AnchorPanels.MUTED);

            x += width + pillGap();
        }
    }

    private record Pill(String value, String label) {}

    private static final int PILL_PADDING = 5;
    private static final int PILL_SPACING = 4;

    private Pill[] pillContents() {
        return new Pill[] {
                new Pill(String.valueOf(menu.getActionCount()),
                        text("gui.chronoclones.anchor.pill.actions")),
                new Pill(String.valueOf(menu.getActiveClones()),
                        text("gui.chronoclones.anchor.pill.clones")),
                new Pill("×" + menu.getTicksPerStep(),
                        text("gui.chronoclones.anchor.pill.rate")),
        };
    }

    private int pillWidth(Pill pill) {
        return PILL_PADDING * 2 + font.width(pill.value()) + PILL_SPACING + font.width(pill.label());
    }

    /** Whatever the three pills do not use, shared between them. */
    private int pillGap() {
        int used = 0;
        for (Pill pill : pillContents()) {
            used += pillWidth(pill);
        }
        return Math.max(2, (Layout.CONTENT_WIDTH - used) / 2);
    }

    private void storagePanel(GuiGraphicsExtractor extractor, int xo, int yo) {
        AnchorPanels.panel(extractor, xo + Layout.MARGIN, yo + Layout.STORAGE_PANEL_Y,
                Layout.CONTENT_WIDTH, Layout.STORAGE_PANEL_HEIGHT);

        for (int row = 0; row < Layout.STORAGE_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                AnchorPanels.slot(extractor, xo + Layout.GRID_X + col * 18,
                        yo + Layout.STORAGE_Y + row * 18);
            }
        }
    }

    private void chargePanel(GuiGraphicsExtractor extractor, int xo, int yo) {
        AnchorPanels.panel(extractor, xo + Layout.MARGIN, yo + Layout.MODULE_PANEL_Y,
                Layout.CHARGE_PANEL_WIDTH, Layout.MODULE_PANEL_HEIGHT);

        AnchorPanels.slot(extractor, xo + Layout.FUEL_X, yo + Layout.MODULE_Y);
        if (!menu.slots.get(ChronoAnchorMenu.FUEL_SLOT).hasItem()) {
            AnchorPanels.fuelIcon(extractor, xo + Layout.FUEL_X + 4, yo + Layout.MODULE_Y + 3,
                    AnchorPanels.SLOT_EDGE);
        }

        AnchorPanels.bar(extractor, xo + Layout.CHARGE_X, yo + Layout.CHARGE_Y,
                Layout.CHARGE_WIDTH, Layout.CHARGE_HEIGHT, chargeFraction(), AnchorPanels.ACCENT);

        String percent = Math.round(chargeFraction() * 100) + "%";
        extractor.text(font, percent, xo + Layout.CHARGE_X, yo + Layout.CHARGE_TEXT_Y,
                AnchorPanels.ACCENT);
    }

    private void modulePanel(GuiGraphicsExtractor extractor, int xo, int yo) {
        AnchorPanels.panel(extractor, xo + Layout.MODULE_PANEL_X, yo + Layout.MODULE_PANEL_Y,
                Layout.WIDTH - Layout.MARGIN - Layout.MODULE_PANEL_X, Layout.MODULE_PANEL_HEIGHT);

        for (int i = 0; i < 3; i++) {
            int x = xo + Layout.UPGRADE_X + i * 18;
            AnchorPanels.slot(extractor, x, yo + Layout.MODULE_Y);
            if (!menu.slots.get(ChronoAnchorMenu.FUEL_SLOT + 1 + i).hasItem()) {
                AnchorPanels.moduleIcon(extractor, x + 3, yo + Layout.MODULE_Y + 3,
                        AnchorPanels.SLOT_EDGE);
            }
        }
    }

    /**
     * Always drawn: calm when the routine is running, so nothing shifts when it stops being.
     */
    private void diagnosticBanner(GuiGraphicsExtractor extractor, int xo, int yo) {
        DiagnosticState.FailureReason reason = reasonOf(menu.getFailureOrdinal());
        int edge = bannerEdge(reason);
        int fill = bannerFill(reason);

        int x = xo + Layout.MARGIN;
        int y = yo + Layout.DIAGNOSTIC_Y;
        AnchorPanels.box(extractor, x, y, Layout.CONTENT_WIDTH, Layout.DIAGNOSTIC_HEIGHT, edge, fill);

        if (reason != DiagnosticState.FailureReason.NONE) {
            AnchorPanels.warningIcon(extractor, x + 4, y + 2, edge, fill);
        }
    }

    private void playerInventory(GuiGraphicsExtractor extractor, int xo, int yo) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                AnchorPanels.slot(extractor, xo + Layout.GRID_X + col * 18,
                        yo + Layout.PLAYER_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            AnchorPanels.slot(extractor, xo + Layout.GRID_X + col * 18, yo + Layout.HOTBAR_Y);
        }
    }

    /**
     * The precision drawer, as wide as it has slid so far.
     */
    private void drawer(GuiGraphicsExtractor extractor, int xo, int yo, float partialTick) {
        float eased = previousOpenness + (openness - previousOpenness) * partialTick;
        int open = Math.round(DrawerLayout.WIDTH * eased);
        if (open <= 0) {
            return;
        }

        int x = DrawerLayout.bodyX(drawerOnLeft, xo, imageWidth, open);
        int top = yo + DRAWER_TAB_Y;
        AnchorPanels.box(extractor, x, top, open, DRAWER_HEIGHT,
                AnchorPanels.WINDOW_EDGE, AnchorPanels.WINDOW);

        // The title only once there is room for it, rather than sliding in clipped from the edge.
        if (open >= DrawerLayout.WIDTH) {
            extractor.text(font, Component.translatable("gui.chronoclones.anchor.settings"),
                    x + DRAWER_PADDING, top + DRAWER_TITLE_Y, AnchorPanels.ACCENT);
            extractor.text(font, Component.translatable("gui.chronoclones.anchor.settings.empty"),
                    x + DRAWER_PADDING, top + DRAWER_TITLE_Y + 14, AnchorPanels.MUTED);
        }
    }

    // ------------------------------------------------------------------ labels

    /** Wrapped in a translate to leftPos/topPos, so these coordinates are window-local. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(font, title, Layout.MARGIN, Layout.TITLE_Y, AnchorPanels.TEXT);
        status(extractor);

        cloneTabs(extractor);
        AnchorPanels.heading(extractor, font, text("gui.chronoclones.anchor.section.storage"),
                Layout.MARGIN + 4, Layout.STORAGE_HEADER_Y);

        AnchorPanels.heading(extractor, font, text("gui.chronoclones.anchor.section.charge"),
                Layout.MARGIN + 4, Layout.SECTION_LABEL_Y);
        AnchorPanels.heading(extractor, font, text("gui.chronoclones.anchor.section.modules"),
                Layout.MODULE_PANEL_X + 4, Layout.SECTION_LABEL_Y);

        diagnosticText(extractor);
    }

    /** Right-aligned against the title: the routine's length, or that there is not one. */
    private void status(GuiGraphicsExtractor extractor) {
        Component line = menu.getLengthTicks() <= 0
                ? Component.translatable("gui.chronoclones.anchor.no_recording")
                : Component.translatable("gui.chronoclones.anchor.elapsed",
                        menu.getPlayhead(0) / 20, menu.getLengthTicks() / 20);

        extractor.text(font, line, Layout.WIDTH - Layout.MARGIN - font.width(line),
                Layout.TITLE_Y, menu.getLengthTicks() <= 0 ? AnchorPanels.MUTED : AnchorPanels.ACCENT);
    }

    private void diagnosticText(GuiGraphicsExtractor extractor) {
        DiagnosticState.FailureReason reason = reasonOf(menu.getFailureOrdinal());
        int y = Layout.DIAGNOSTIC_Y + 1;

        if (reason == DiagnosticState.FailureReason.NONE) {
            extractor.text(font, Component.translatable("diagnostic.chronoclones.none"),
                    Layout.MARGIN + 6, y, AnchorPanels.ACCENT);
            return;
        }

        BlockPos at = menu.getFailurePos();
        String where = String.format("%+d, %+d, %+d", at.getX(), at.getY(), at.getZ());
        extractor.text(font, Component.translatable(reason.translationKey(), where),
                Layout.MARGIN + 16, y, bannerEdge(reason));
    }

    /**
     * The clone tabs, sharing the storage header row.
     */
    private void cloneTabs(GuiGraphicsExtractor extractor) {
        int tabs = CloneTabs.count(menu.getActiveClones());
        int selected = menu.getSelectedClone();

        for (int tab = 0; tab < tabs; tab++) {
            int x = CloneTabs.xOf(tab, tabs, Layout.TAB_RIGHT_EDGE);
            int y = Layout.TAB_Y;
            boolean current = tab == selected;

            AnchorPanels.box(extractor, x, y, CloneTabs.WIDTH, CloneTabs.HEIGHT,
                    current ? AnchorPanels.ACCENT : AnchorPanels.PANEL_EDGE,
                    current ? AnchorPanels.PANEL : AnchorPanels.WINDOW);

            String label = String.valueOf(tab + 1);
            extractor.text(font, label, x + (CloneTabs.WIDTH - font.width(label)) / 2, y + 1,
                    current ? AnchorPanels.ACCENT : AnchorPanels.MUTED);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubled) {
        int tabs = CloneTabs.count(menu.getActiveClones());
        int tab = CloneTabs.at((int) event.x() - leftPos, (int) event.y() - topPos, tabs,
                Layout.TAB_RIGHT_EDGE, Layout.TAB_Y);

        if (tab >= 0 && tab != menu.getSelectedClone() && minecraft != null && minecraft.gameMode != null) {
            // Both sides act on the same click: the menu has no synced field for the selection.
            menu.clickMenuButton(minecraft.player, tab);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab);
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    /**
     * Tooltips for the parts of the window that are not slots.
     */
    @Override
    protected void extractTooltip(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        super.extractTooltip(extractor, mouseX, mouseY);
        // A slot under the pointer has already claimed the tooltip, and two at once is one too many.
        if (hoveredSlot != null) {
            return;
        }

        if (within(mouseX, mouseY, Layout.CHARGE_X, Layout.CHARGE_Y,
                Layout.CHARGE_WIDTH, Layout.CHARGE_HEIGHT)) {
            // The bar answers "roughly how full"; this answers "will it finish".
            tooltip(extractor, mouseX, mouseY, Component.translatable(
                    "gui.chronoclones.anchor.charge.detail", menu.getCharge(), menu.getChargeCapacity()));
        } else if (within(mouseX, mouseY, Layout.FUEL_X, Layout.MODULE_Y, 16, 16)) {
            tooltip(extractor, mouseX, mouseY,
                    Component.translatable("gui.chronoclones.anchor.section.fuel.tip"));
        } else if (within(mouseX, mouseY, Layout.UPGRADE_X, Layout.MODULE_Y, 16 + 2 * 18, 16)) {
            tooltip(extractor, mouseX, mouseY,
                    Component.translatable("gui.chronoclones.anchor.section.modules.tip"));
        } else if (within(mouseX, mouseY, Layout.MARGIN, Layout.TIMELINE_Y,
                Layout.CONTENT_WIDTH, Layout.TIMELINE_HEIGHT) && menu.getLengthTicks() > 0) {
            tooltip(extractor, mouseX, mouseY, Component.translatable(
                    "gui.chronoclones.anchor.timeline.tip",
                    menu.getLengthTicks() / 20, menu.getActionCount()));
        }
    }

    private void tooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY, Component text) {
        extractor.setTooltipForNextFrame(font, text, mouseX, mouseY);
    }

    /** Whether the mouse is over a window-local rectangle, both in screen coordinates. */
    private boolean within(int mouseX, int mouseY, int x, int y, int width, int height) {
        int left = leftPos + x;
        int top = topPos + y;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    // ------------------------------------------------------------------ readings

    private float chargeFraction() {
        return (float) menu.getCharge() / menu.getChargeCapacity();
    }

    private String text(String key) {
        return Component.translatable(key).getString();
    }

    private static int bannerEdge(DiagnosticState.FailureReason reason) {
        if (reason == DiagnosticState.FailureReason.NONE) {
            return AnchorPanels.ACCENT;
        }
        return reason.halts() ? AnchorPanels.HALTED : AnchorPanels.WARNING;
    }

    private static int bannerFill(DiagnosticState.FailureReason reason) {
        if (reason == DiagnosticState.FailureReason.NONE) {
            return AnchorPanels.RUNNING_FILL;
        }
        return reason.halts() ? AnchorPanels.HALTED_FILL : AnchorPanels.WARNING_FILL;
    }

    private static DiagnosticState.FailureReason reasonOf(int ordinal) {
        DiagnosticState.FailureReason[] values = DiagnosticState.FailureReason.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DiagnosticState.FailureReason.NONE;
    }
}
