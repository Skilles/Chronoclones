package com.skilles.chronoclones.menu.client;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import com.skilles.chronoclones.network.RoutinePayloads;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;

/**
 * The Chrono Anchor screen: a timeline, the running totals, one clone's storage, charge and
 * modules, the diagnostic, and the player's own inventory.
 */
public class ChronoAnchorScreen extends AbstractContainerScreen<ChronoAnchorMenu> {

    /** Level with the storage band, so the handle reads as attached to a section. */
    private static final int EDITOR_TAB_Y = Layout.BAND_Y;

    private DrawerTab editorTab;

    public ChronoAnchorScreen(ChronoAnchorMenu menu, Inventory playerInventory, Component title) {
        // imageWidth/imageHeight are final in 26.x and must go through the 5-arg constructor.
        super(menu, playerInventory, title, Layout.WIDTH, Layout.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        editorTab = addRenderableWidget(new DrawerTab(
                Component.translatable("gui.chronoclones.anchor.settings"),
                () -> ClientPacketDistributor.sendToServer(new RoutinePayloads.Request(
                        RoutinePayloads.Source.ofAnchor(menu.getAnchorPos())))));
        editorTab.setPosition(DrawerTab.tabX(leftPos, imageWidth), topPos + EDITOR_TAB_Y);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        // Nothing to edit until something is imprinted, and the handle should say so.
        editorTab.active = menu.getLengthTicks() > 0;
    }

    /** Not inside the container's pose translation, so coordinates here are absolute. */
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(extractor, mouseX, mouseY, partialTick);

        int xo = leftPos;
        int yo = topPos;

        AnchorPanels.panel(extractor, xo - 2, yo - 2, imageWidth + 4, imageHeight + 4);
        extractor.fill(xo, yo, xo + imageWidth, yo + imageHeight, AnchorPanels.WINDOW);

        timeline(extractor, xo, yo);
        pills(extractor, xo, yo);
        rail(extractor, xo, yo);
        storagePanel(extractor, xo, yo);
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

        AnchorPanels.track(extractor, x, y, width, height);

        int length = menu.getLengthTicks();
        if (length <= 0) {
            return;
        }

        for (int tick : menu.getActionTicks()) {
            int at = x + (width - 1) * Math.clamp(tick, 0, length) / length;
            extractor.fill(at, y, at + 1, y + height, AnchorPanels.ACCENT);
        }

        for (int clone = 0; clone < menu.getActiveClones(); clone++) {
            int playhead = Math.clamp(menu.getPlayhead(clone), 0, length);
            int at = x + (width - 1) * playhead / length;
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
            int at = x + PILL_PADDING;

            AnchorPanels.icon(extractor, pill.icon(), at,
                    y + (Layout.PILLS_HEIGHT - AnchorPanels.ICON_SIZE) / 2, AnchorPanels.ACCENT);
            at += AnchorPanels.ICON_SIZE + PILL_SPACING;

            extractor.text(font, pill.value(), at, textY, AnchorPanels.ACCENT);
            at += font.width(pill.value()) + PILL_SPACING;

            extractor.text(font, pill.label(), at, textY, AnchorPanels.MUTED);

            x += width + pillGap();
        }
    }

    private record Pill(Identifier icon, String value, String label) {}

    private static final int PILL_PADDING = 5;
    private static final int PILL_SPACING = 4;

    private Pill[] pillContents() {
        return new Pill[] {
                new Pill(AnchorPanels.ICON_ACTIONS, String.valueOf(menu.getActionCount()),
                        text("gui.chronoclones.anchor.pill.actions")),
                new Pill(AnchorPanels.ICON_CLONES, String.valueOf(menu.getActiveClones()),
                        text("gui.chronoclones.anchor.pill.clones")),
                new Pill(AnchorPanels.ICON_RATE, "×" + menu.getTicksPerStep(),
                        text("gui.chronoclones.anchor.pill.rate")),
        };
    }

    private int pillWidth(Pill pill) {
        return PILL_PADDING * 2 + AnchorPanels.ICON_SIZE + PILL_SPACING
                + font.width(pill.value()) + PILL_SPACING + font.width(pill.label());
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
        AnchorPanels.panel(extractor, xo + Layout.STORAGE_PANEL_X, yo + Layout.BAND_Y,
                Layout.STORAGE_PANEL_WIDTH, Layout.BAND_HEIGHT);

        for (int row = 0; row < Layout.STORAGE_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                AnchorPanels.slot(extractor, xo + Layout.STORAGE_X + col * 18,
                        yo + Layout.STORAGE_Y + row * 18);
            }
        }
    }

    /**
     * Fuel, charge and the modules, standing beside the storage grid rather than under it.
     */
    private void rail(GuiGraphicsExtractor extractor, int xo, int yo) {
        AnchorPanels.panel(extractor, xo + Layout.RAIL_X, yo + Layout.BAND_Y,
                Layout.RAIL_WIDTH, Layout.BAND_HEIGHT);

        AnchorPanels.slot(extractor, xo + Layout.FUEL_X, yo + Layout.MODULE_Y);
        if (!menu.slots.get(ChronoAnchorMenu.FUEL_SLOT).hasItem()) {
            AnchorPanels.icon(extractor, AnchorPanels.ICON_FUEL, xo + Layout.FUEL_X + 4,
                    yo + Layout.MODULE_Y + 4, AnchorPanels.SLOT_EDGE);
        }

        for (int i = 0; i < 3; i++) {
            int y = yo + Layout.MODULE_Y + (i + 1) * 18;
            AnchorPanels.slot(extractor, xo + Layout.UPGRADE_X, y);
            if (!menu.slots.get(ChronoAnchorMenu.FUEL_SLOT + 1 + i).hasItem()) {
                AnchorPanels.icon(extractor, AnchorPanels.ICON_MODULE, xo + Layout.UPGRADE_X + 4,
                        y + 4, AnchorPanels.SLOT_EDGE);
            }
        }

        AnchorPanels.barUp(extractor, xo + Layout.CHARGE_X, yo + Layout.CHARGE_Y,
                Layout.CHARGE_WIDTH, Layout.CHARGE_HEIGHT, chargeFraction(), AnchorPanels.ACCENT);
    }

    private void playerInventory(GuiGraphicsExtractor extractor, int xo, int yo) {
        AnchorPanels.panel(extractor, xo + Layout.MARGIN, yo + Layout.INVENTORY_PANEL_Y,
                Layout.CONTENT_WIDTH, Layout.INVENTORY_PANEL_HEIGHT);

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

    // ------------------------------------------------------------------ labels

    /** Wrapped in a translate to leftPos/topPos, so these coordinates are window-local. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(font, title, Layout.MARGIN, Layout.TITLE_Y, AnchorPanels.TEXT);
        status(extractor);

        DiagnosticState.FailureReason reason = reasonOf(menu.getFailureOrdinal());
        if (reason != DiagnosticState.FailureReason.NONE) {
            AnchorPanels.icon(extractor, AnchorPanels.ICON_WARNING, warningX(),
                    Layout.TITLE_Y, bannerEdge(reason));
        }

        AnchorPanels.legend(extractor, font, text("gui.chronoclones.anchor.section.storage"),
                Layout.STORAGE_PANEL_X + 8, Layout.BAND_Y);
        AnchorPanels.legend(extractor, font, playerInventoryTitle.getString(),
                Layout.LEGEND_X, Layout.INVENTORY_PANEL_Y);

        cloneTabs(extractor);
    }

    /** Right-aligned against the title: how full, and how far through. */
    private void status(GuiGraphicsExtractor extractor) {
        Component line = menu.getLengthTicks() <= 0
                ? Component.translatable("gui.chronoclones.anchor.no_recording")
                : Component.translatable("gui.chronoclones.anchor.elapsed",
                        menu.getPlayhead(0) / 20, menu.getLengthTicks() / 20);

        int right = Layout.WIDTH - Layout.MARGIN;
        extractor.text(font, line, right - font.width(line), Layout.TITLE_Y,
                menu.getLengthTicks() <= 0 ? AnchorPanels.MUTED : AnchorPanels.ACCENT);

        // The upright bar has no room to letter itself, so the number lives up here.
        String percent = Math.round(chargeFraction() * 100) + "%";
        extractor.text(font, percent, right - font.width(line) - PILL_SPACING * 2 - font.width(percent),
                Layout.TITLE_Y, AnchorPanels.MUTED);
    }

    /** Just past the title, so a routine in trouble says so without a band of its own. */
    private int warningX() {
        return Layout.MARGIN + font.width(title) + 6;
    }

    /**
     * The clone tabs, straddling the storage panel's top border.
     */
    private void cloneTabs(GuiGraphicsExtractor extractor) {
        int tabs = CloneTabs.count(menu.getActiveClones());
        int selected = menu.getSelectedClone();

        for (int tab = 0; tab < tabs; tab++) {
            int x = CloneTabs.xOf(tab, tabs, Layout.TAB_RIGHT_EDGE);
            int y = Layout.TAB_Y;
            boolean current = tab == selected;

            extractor.fill(x, y, x + CloneTabs.WIDTH, y + CloneTabs.HEIGHT, AnchorPanels.WINDOW);
            AnchorPanels.outline(extractor, x, y, CloneTabs.WIDTH, CloneTabs.HEIGHT,
                    current ? AnchorPanels.ACCENT : AnchorPanels.SLOT_EDGE);

            String label = String.valueOf(tab + 1);
            extractor.text(font, label, x + (CloneTabs.WIDTH - font.width(label)) / 2,
                    y + (CloneTabs.HEIGHT - font.lineHeight) / 2 + 1,
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
        } else if (within(mouseX, mouseY, Layout.UPGRADE_X, Layout.MODULE_Y + 18, 16, 16 + 2 * 18)) {
            tooltip(extractor, mouseX, mouseY,
                    Component.translatable("gui.chronoclones.anchor.section.modules.tip"));
        } else if (within(mouseX, mouseY, warningX(), Layout.TITLE_Y,
                AnchorPanels.ICON_SIZE, AnchorPanels.ICON_SIZE)
                && reasonOf(menu.getFailureOrdinal()) != DiagnosticState.FailureReason.NONE) {
            BlockPos at = menu.getFailurePos();
            tooltip(extractor, mouseX, mouseY, Component.translatable(
                    reasonOf(menu.getFailureOrdinal()).translationKey(),
                    String.format("%+d, %+d, %+d", at.getX(), at.getY(), at.getZ())));
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

    /** Amber for something the routine will get past, red for something it will not. */
    private static int bannerEdge(DiagnosticState.FailureReason reason) {
        return reason.halts() ? AnchorPanels.HALTED : AnchorPanels.WARNING;
    }

    private static DiagnosticState.FailureReason reasonOf(int ordinal) {
        DiagnosticState.FailureReason[] values = DiagnosticState.FailureReason.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DiagnosticState.FailureReason.NONE;
    }
}
