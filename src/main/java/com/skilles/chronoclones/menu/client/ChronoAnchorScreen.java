package com.skilles.chronoclones.menu.client;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Chrono Anchor screen: storage, fuel, upgrades, charge, and the diagnostic line.
 *
 * <p>26.x replaced immediate-mode screen drawing with render-state extraction — {@code renderBg},
 * {@code GuiGraphics} and {@code blit} are gone. The direct replacement for {@code renderBg} is
 * {@link net.minecraft.client.gui.screens.Screen#extractBackground}, which is what vanilla's
 * {@code HopperScreen} overrides.
 *
 * <p>Drawn with {@code fill} and {@code text} rather than a blitted texture. That started as a way
 * to de-risk the GUI spike without also depending on a texture asset and the {@code RenderPipeline}
 * argument every {@code blit} overload now requires; it has stayed because it costs nothing and
 * reads cleanly. A painted texture is a polish-pass swap, not a rewrite.
 *
 * <p>Client-only. Isolation comes from being referenced solely by {@code ChronoclonesClient}, which
 * is itself {@code @Mod(dist = Dist.CLIENT)} — 26.x removed the runtime member-stripping that
 * {@code @OnlyIn} used to provide, so that annotation is noise and NeoForge warns about it.
 */
public class ChronoAnchorScreen extends AbstractContainerScreen<ChronoAnchorMenu> {

    private static final int PANEL_BG = 0xFF2B2B33;
    private static final int PANEL_EDGE = 0xFF5A5A6E;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int TEXT = 0xFFE0E0E8;
    private static final int ACCENT = 0xFF7FD4C1;
    private static final int MUTED = 0xFF8A8A99;
    private static final int WARNING = 0xFFE0B860;
    private static final int HALTED = 0xFFE06060;
    private static final int CHARGE_FULL = 0xFF7FD4C1;
    private static final int CHARGE_EMPTY = 0xFF3A3A45;

    public ChronoAnchorScreen(ChronoAnchorMenu menu, Inventory playerInventory, Component title) {
        // imageWidth/imageHeight are final in 26.x — they must go through the 5-arg constructor.
        super(menu, playerInventory, title, Layout.WIDTH, Layout.HEIGHT);
        this.inventoryLabelY = Layout.PLAYER_LABEL_Y;
    }

    /**
     * Window background. This is NOT inside the container's pose translation, so coordinates here
     * are absolute — same as vanilla's {@code HopperScreen}, which computes its own origin rather
     * than reading {@code leftPos}. Calling {@code super} first paints the usual screen dimming.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(extractor, mouseX, mouseY, partialTick);

        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;

        extractor.fill(xo - 1, yo - 1, xo + imageWidth + 1, yo + imageHeight + 1, PANEL_EDGE);
        extractor.fill(xo, yo, xo + imageWidth, yo + imageHeight, PANEL_BG);

        // Storage grid, then fuel + upgrades, then the player inventory — matching menu slot order.
        for (int row = 0; row < 2; row++) {
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

    /**
     * Labels. {@code extractContents} wraps this call in {@code pose().translate(leftPos, topPos)},
     * so these coordinates are LOCAL to the window — adding leftPos/topPos here offsets everything
     * a second time. Vanilla's own override uses bare {@code titleLabelX}/{@code titleLabelY}.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(font, title, titleLabelX, titleLabelY, TEXT);
        extractor.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT);

        if (menu.getLengthTicks() <= 0) {
            extractor.text(font, Component.translatable("gui.chronoclones.anchor.no_recording"),
                    8, Layout.STATUS_Y, MUTED);
            return;
        }

        // Each readout gets its own line. Packing them side by side overlapped as soon as a
        // routine had a two-digit action count.
        extractor.text(font, Component.translatable("gui.chronoclones.anchor.progress",
                        menu.getPlayhead() / 20, menu.getLengthTicks() / 20, menu.getActionCount()),
                8, Layout.STATUS_Y, ACCENT);

        // Matching is on the same line as the other axes: a routine that skips everything because
        // the stone became deepslate is otherwise indistinguishable from one that is simply broken.
        extractor.text(font, Component.translatable("gui.chronoclones.anchor.upgrades",
                        menu.getActiveClones(), menu.getTicksPerStep(),
                        Component.translatable(menu.getCoherenceTier() >= 1
                                ? "gui.chronoclones.anchor.matching.exact"
                                : "gui.chronoclones.anchor.matching.lenient")),
                8, Layout.UPGRADE_INFO_Y, MUTED);

        // Only when it has been moved. A permanent "Origin 0, 0, 0" line would be noise on every
        // anchor to explain a feature most of them are not using.
        net.minecraft.core.BlockPos offset = menu.getOriginOffset();
        if (!offset.equals(net.minecraft.core.BlockPos.ZERO)) {
            extractor.text(font, Component.translatable("gui.chronoclones.anchor.origin",
                            offset.getX(), offset.getY(), offset.getZ()),
                    8, Layout.STATUS_Y - 10, MUTED);
        }

        // The diagnostic line the spec insists on: not just what failed, but where, in
        // anchor-local coordinates, so the failing block can actually be found.
        DiagnosticState.FailureReason reason = reasonOf(menu.getFailureOrdinal());
        if (reason != DiagnosticState.FailureReason.NONE) {
            BlockPos at = menu.getFailurePos();
            String where = String.format("%+d, %+d, %+d", at.getX(), at.getY(), at.getZ());
            extractor.text(font, Component.translatable(reason.translationKey(), where),
                    8, Layout.DIAGNOSTIC_Y, reason.halts() ? HALTED : WARNING);
        }
    }

    private static DiagnosticState.FailureReason reasonOf(int ordinal) {
        DiagnosticState.FailureReason[] values = DiagnosticState.FailureReason.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DiagnosticState.FailureReason.NONE;
    }
}
