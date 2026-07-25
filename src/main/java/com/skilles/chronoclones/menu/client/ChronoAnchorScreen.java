package com.skilles.chronoclones.menu.client;

import com.skilles.chronoclones.menu.ChronoAnchorMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * DAY 1 SPIKE (1c). Prices the 26.x GUI rewrite before the schedule depends on it.
 *
 * <p>26.x replaced immediate-mode screen drawing with render-state extraction:
 * {@code renderBg} / {@code GuiGraphics} / {@code blit} are gone, and the direct replacement for
 * {@code renderBg} is {@link net.minecraft.client.gui.screens.Screen#extractBackground}, which is
 * exactly what vanilla's {@code HopperScreen} overrides.
 *
 * <p>This deliberately draws with {@code fill} and {@code text} rather than a blitted texture, so
 * the spike proves the menu/slot/screen plumbing without also depending on a texture asset and the
 * new {@code RenderPipeline} parameter that every {@code blit} overload now requires. Texturing is
 * a Day 8 concern.
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

    public ChronoAnchorScreen(ChronoAnchorMenu menu, Inventory playerInventory, Component title) {
        // imageWidth/imageHeight are final in 26.x — they must go through the 5-arg constructor.
        super(menu, playerInventory, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
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

        // Anchor inventory slots (18) then the player inventory block, matching menu slot layout.
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                slotBox(extractor, xo + 8 + col * 18, yo + 18 + row * 18);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotBox(extractor, xo + 8 + col * 18, yo + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotBox(extractor, xo + 8 + col * 18, yo + 142);
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

        // Spike readout: proves ContainerData is syncing server -> client every tick.
        int length = menu.getLengthTicks();
        String status = length <= 0
                ? "idle"
                : String.format("t %d/%d  loops %d", menu.getPlayhead(), length, menu.getLoopsCompleted());
        extractor.text(font, status, 8, 60, ACCENT);
    }
}
