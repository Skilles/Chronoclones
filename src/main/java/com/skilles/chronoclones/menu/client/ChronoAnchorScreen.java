package com.skilles.chronoclones.menu.client;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;

/**
 * The Chrono Anchor screen: storage, fuel, upgrades, charge, and the diagnostic line.
 */
public class ChronoAnchorScreen extends AbstractContainerScreen<ChronoAnchorMenu> {

    // Package-private: the drawer widgets draw themselves in the same palette.
    static final int PANEL_BG = 0xFF2B2B33;
    static final int PANEL_EDGE = 0xFF5A5A6E;
    static final int SLOT_BG = 0xFF8B8B8B;
    static final int TEXT = 0xFFE0E0E8;
    static final int ACCENT = 0xFF7FD4C1;
    static final int MUTED = 0xFF8A8A99;
    private static final int WARNING = 0xFFE0B860;
    private static final int HALTED = 0xFFE06060;
    private static final int CHARGE_FULL = 0xFF7FD4C1;
    private static final int CHARGE_EMPTY = 0xFF3A3A45;

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
        this.inventoryLabelY = Layout.PLAYER_LABEL_Y;
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

        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;

        // Before the window, so a drawer still sliding out passes behind it rather than over it.
        drawer(extractor, xo, yo, partialTick);

        extractor.fill(xo - 1, yo - 1, xo + imageWidth + 1, yo + imageHeight + 1, PANEL_EDGE);
        extractor.fill(xo, yo, xo + imageWidth, yo + imageHeight, PANEL_BG);

        // Storage grid, then fuel and upgrades, then the player inventory: menu slot order.
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                slotBox(extractor, xo + 8 + col * 18, yo + Layout.STORAGE_Y + row * 18);
            }
        }

        slotBox(extractor, xo + Layout.FUEL_X, yo + Layout.MODULE_Y);
        for (int i = 0; i < 3; i++) {
            slotBox(extractor, xo + Layout.UPGRADE_X + i * 18, yo + Layout.MODULE_Y);
        }

        chargeBar(extractor, xo + Layout.CHARGE_X, yo + Layout.CHARGE_Y);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotBox(extractor, xo + 8 + col * 18, yo + Layout.PLAYER_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotBox(extractor, xo + 8 + col * 18, yo + Layout.HOTBAR_Y);
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

        extractor.fill(x - 1, top - 1, x + open + 1, top + DRAWER_HEIGHT + 1, PANEL_EDGE);
        extractor.fill(x, top, x + open, top + DRAWER_HEIGHT, PANEL_BG);

        // The title only once there is room for it, rather than sliding in clipped from the edge.
        if (open >= DrawerLayout.WIDTH) {
            extractor.text(font, Component.translatable("gui.chronoclones.anchor.settings"),
                    x + DRAWER_PADDING, top + DRAWER_TITLE_Y, ACCENT);
            extractor.text(font, Component.translatable("gui.chronoclones.anchor.settings.empty"),
                    x + DRAWER_PADDING, top + DRAWER_TITLE_Y + 14, MUTED);
        }
    }

    /** Charge is the balance lever, so it gets a bar rather than a number buried in text. */
    private void chargeBar(GuiGraphicsExtractor extractor, int x, int y) {
        int width = Layout.CHARGE_WIDTH;
        int height = Layout.CHARGE_HEIGHT;

        extractor.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_EDGE);
        extractor.fill(x, y, x + width, y + height, CHARGE_EMPTY);

        int filled = menu.getCharge() * width / menu.getChargeCapacity();
        if (filled > 0) {
            extractor.fill(x, y, x + filled, y + height, CHARGE_FULL);
        }
    }

    private void slotBox(GuiGraphicsExtractor extractor, int x, int y) {
        extractor.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
    }

    /** Wrapped in a translate to leftPos/topPos, so these coordinates are window-local. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(font, title, titleLabelX, titleLabelY, TEXT);
        extractor.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT);
        sectionLabels(extractor);

        if (menu.getLengthTicks() <= 0) {
            extractor.text(font, Component.translatable("gui.chronoclones.anchor.no_recording"),
                    8, Layout.STATUS_Y, MUTED);
            return;
        }

        // A line each: side by side overlapped at two-digit action counts.
        extractor.text(font, Component.translatable("gui.chronoclones.anchor.progress",
                        menu.getPlayhead() / 20, menu.getLengthTicks() / 20, menu.getActionCount()),
                8, Layout.STATUS_Y, ACCENT);

        extractor.text(font, Component.translatable("gui.chronoclones.anchor.upgrades",
                        menu.getActiveClones(), menu.getTicksPerStep()),
                8, Layout.UPGRADE_INFO_Y, MUTED);

        // Not just what failed but where, in anchor-local coordinates.
        DiagnosticState.FailureReason reason = reasonOf(menu.getFailureOrdinal());
        if (reason != DiagnosticState.FailureReason.NONE) {
            BlockPos at = menu.getFailurePos();
            String where = String.format("%+d, %+d, %+d", at.getX(), at.getY(), at.getZ());
            extractor.text(font, Component.translatable(reason.translationKey(), where),
                    8, Layout.DIAGNOSTIC_Y, reason.halts() ? HALTED : WARNING);
        }
    }

    private void sectionLabels(GuiGraphicsExtractor extractor) {
        extractor.text(font, Component.translatable("gui.chronoclones.anchor.section.fuel"),
                Layout.FUEL_X, Layout.SECTION_LABEL_Y, MUTED);
        extractor.text(font, Component.translatable("gui.chronoclones.anchor.section.charge"),
                Layout.CHARGE_X, Layout.SECTION_LABEL_Y, MUTED);
        extractor.text(font, Component.translatable("gui.chronoclones.anchor.section.modules"),
                Layout.UPGRADE_X, Layout.SECTION_LABEL_Y, MUTED);
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
            extractor.setTooltipForNextFrame(font,
                    Component.translatable("gui.chronoclones.anchor.charge.detail",
                            menu.getCharge(), menu.getChargeCapacity()),
                    mouseX, mouseY);
        } else if (within(mouseX, mouseY, Layout.FUEL_X, Layout.MODULE_Y, 16, 16)) {
            extractor.setTooltipForNextFrame(font,
                    Component.translatable("gui.chronoclones.anchor.section.fuel.tip"), mouseX, mouseY);
        } else if (within(mouseX, mouseY, Layout.UPGRADE_X, Layout.MODULE_Y, 16 + 2 * 18, 16)) {
            extractor.setTooltipForNextFrame(font,
                    Component.translatable("gui.chronoclones.anchor.section.modules.tip"), mouseX, mouseY);
        }
    }

    /** Whether the mouse is over a window-local rectangle, both in screen coordinates. */
    private boolean within(int mouseX, int mouseY, int x, int y, int width, int height) {
        int left = leftPos + x;
        int top = topPos + y;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private static DiagnosticState.FailureReason reasonOf(int ordinal) {
        DiagnosticState.FailureReason[] values = DiagnosticState.FailureReason.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DiagnosticState.FailureReason.NONE;
    }
}
