package com.skilles.chronoclones.menu.client;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ExperienceStore;
import com.skilles.chronoclones.block.RunState;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.menu.ChronoAnchorMenu.Layout;

import com.skilles.chronoclones.network.RoutinePayloads;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.skilles.chronoclones.platform.PlatformClientNetwork;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.NonNull;

public class ChronoAnchorScreen extends AbstractContainerScreen<ChronoAnchorMenu> {

    private static final int EDITOR_TAB_Y = Layout.BAND_Y;

    private DrawerTab editorTab;

    public ChronoAnchorScreen(ChronoAnchorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, Layout.WIDTH, Layout.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        editorTab = addRenderableWidget(new DrawerTab(
                Component.translatable("gui.chronoclones.anchor.settings"),
                () -> PlatformClientNetwork.sendToServer(new RoutinePayloads.Request(
                        RoutinePayloads.Source.ofAnchor(menu.getAnchorPos())))));
        editorTab.setPosition(DrawerTab.tabX(leftPos, imageWidth), topPos + EDITOR_TAB_Y);
        editorTab.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.chronoclones.anchor.settings.tip")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        editorTab.active = menu.getLengthTicks() > 0;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(extractor, mouseX, mouseY, partialTick);

        int xo = leftPos;
        int yo = topPos;

        hoveredTransport = transportAt(mouseX, mouseY);
        hoveredRedstone = redstoneAt(mouseX, mouseY);

        AnchorPanels.panel(extractor, xo - 2, yo - 2, imageWidth + 4, imageHeight + 4);
        extractor.fill(xo, yo, xo + imageWidth, yo + imageHeight, AnchorPanels.WINDOW);

        timeline(extractor, xo, yo);
        pills(extractor, xo, yo);
        rail(extractor, xo, yo);
        storagePanel(extractor, xo, yo);
        playerInventory(extractor, xo, yo);
    }

    private void timeline(GuiGraphicsExtractor extractor, int xo, int yo) {
        int x = xo + Layout.MARGIN;
        int y = yo + Layout.TIMELINE_Y;
        int width = Layout.TIMELINE_WIDTH;
        int height = Layout.TIMELINE_HEIGHT;

        AnchorPanels.track(extractor, x, y, width, height);
        transport(extractor, xo, yo);

        int length = menu.getLengthTicks();
        if (length <= 0) {
            return;
        }

        for (ChronoAnchorMenu.Mark mark : menu.getActionMarks()) {
            int at = x + (width - 1) * Math.clamp(mark.tick(), 0, length) / length;
            if (!ActionIcon.draw(extractor, mark.icon(), at - MARK_SIZE / 2,
                    y + (height - MARK_SIZE) / 2, MARK_SIZE)) {
                extractor.fill(at, y, at + 1, y + height, AnchorPanels.ACCENT);
            }
        }

        if (menu.getRunState() == RunState.STOPPED) {
            return;
        }
        for (int clone = 0; clone < menu.getActiveClones(); clone++) {
            int playhead = Math.clamp(menu.getPlayhead(clone), 0, length);
            int at = x + (width - 1) * playhead / length;
            AnchorPanels.playhead(extractor, at, y - 1, AnchorPanels.TEXT);
        }
    }

    private static final int MARK_SIZE = 11;

    private static final AnchorPanels.Kind[] TRANSPORT = {
            AnchorPanels.Kind.PLAY, AnchorPanels.Kind.PAUSE, AnchorPanels.Kind.STOP
    };

    private void transport(GuiGraphicsExtractor extractor, int xo, int yo) {
        RunState state = menu.getRunState();

        for (int index = 0; index < TRANSPORT.length; index++) {
            AnchorPanels.transport(extractor, TRANSPORT[index],
                    xo + Layout.transportX(index), yo + Layout.TRANSPORT_Y, Layout.TRANSPORT_SIZE,
                    RunState.byOrdinal(index) == state, index == hoveredTransport);
        }

        AnchorPanels.transport(extractor, AnchorPanels.Kind.REDSTONE,
                xo + Layout.REDSTONE_X, yo + Layout.TRANSPORT_Y, Layout.TRANSPORT_SIZE,
                menu.isObeyingRedstone(), hoveredRedstone);
    }

    private int hoveredTransport = -1;
    private boolean hoveredRedstone;

    private int transportAt(int mouseX, int mouseY) {
        for (int index = 0; index < TRANSPORT.length; index++) {
            if (within(mouseX, mouseY, Layout.transportX(index), Layout.TRANSPORT_Y,
                    Layout.TRANSPORT_SIZE, Layout.TRANSPORT_SIZE)) {
                return index;
            }
        }
        return -1;
    }

    private boolean redstoneAt(int mouseX, int mouseY) {
        return within(mouseX, mouseY, Layout.REDSTONE_X, Layout.TRANSPORT_Y,
                Layout.TRANSPORT_SIZE, Layout.TRANSPORT_SIZE);
    }

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

        experienceBar(extractor, xo, yo);

        if (!menu.hasStorage()) {
            extractor.fill(xo + Layout.STORAGE_X - 1, yo + Layout.STORAGE_Y - 1,
                    xo + Layout.STORAGE_X - 1 + 9 * 18, yo + Layout.CLONE_XP_Y
                            + Layout.CLONE_XP_HEIGHT + 1,
                    AnchorPanels.wash(AnchorPanels.WINDOW, 190));
        }
    }

    private void experienceBar(GuiGraphicsExtractor extractor, int xo, int yo) {
        int points = menu.getCloneExperience(menu.getSelectedClone());
        int level = ExperienceStore.levelOf(points);

        String label = String.valueOf(level);
        int labelWidth = Math.max(font.width(label), 8);

        int x = xo + Layout.STORAGE_X;
        int y = yo + Layout.CLONE_XP_Y;
        int width = 9 * 18 - 2 - labelWidth - 4;

        extractor.text(font, label, x, y - 1, AnchorPanels.LEVEL);
        AnchorPanels.bar(extractor, x + labelWidth + 4, y, width, Layout.CLONE_XP_HEIGHT,
                ExperienceStore.progressOf(points), AnchorPanels.LEVEL);
    }

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

    private void status(GuiGraphicsExtractor extractor) {
        Component line = menu.getLengthTicks() <= 0
                ? Component.translatable("gui.chronoclones.anchor.no_recording")
                : Component.translatable("gui.chronoclones.anchor.elapsed",
                        menu.getPlayhead(0) / 20, menu.getLengthTicks() / 20);

        int right = Layout.WIDTH - Layout.MARGIN;
        extractor.text(font, line, right - font.width(line), Layout.TITLE_Y,
                menu.getLengthTicks() <= 0 ? AnchorPanels.MUTED : AnchorPanels.ACCENT);

        String percent = Math.round(chargeFraction() * 100) + "%";
        extractor.text(font, percent, right - font.width(line) - PILL_SPACING * 2 - font.width(percent),
                Layout.TITLE_Y, AnchorPanels.MUTED);
    }

    private int warningX() {
        return Layout.MARGIN + font.width(title) + 6;
    }

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

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubled) {
        int control = transportAt((int) event.x(), (int) event.y());
        if (control >= 0 && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    ChronoAnchorMenu.RUN_STATE_BUTTON + control);
            return true;
        }
        if (redstoneAt((int) event.x(), (int) event.y())
                && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                    ChronoAnchorMenu.REDSTONE_BUTTON);
            return true;
        }

        int tabs = CloneTabs.count(menu.getActiveClones());
        int tab = CloneTabs.at((int) event.x() - leftPos, (int) event.y() - topPos, tabs,
                Layout.TAB_RIGHT_EDGE, Layout.TAB_Y);

        if (tab >= 0 && tab != menu.getSelectedClone() && minecraft != null && minecraft.gameMode != null) {
            menu.clickMenuButton(minecraft.player, tab);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab);
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    protected void extractTooltip(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        super.extractTooltip(extractor, mouseX, mouseY);
        if (hoveredSlot != null) {
            return;
        }

        if (within(mouseX, mouseY, Layout.CHARGE_X, Layout.CHARGE_Y,
                Layout.CHARGE_WIDTH, Layout.CHARGE_HEIGHT)) {
            tooltip(extractor, mouseX, mouseY, Component.translatable(
                    "gui.chronoclones.anchor.charge.detail", menu.getCharge(), menu.getChargeCapacity()));
        } else if (within(mouseX, mouseY, Layout.STORAGE_X, Layout.CLONE_XP_Y,
                9 * 18 - 2, Layout.CLONE_XP_HEIGHT)) {
            int points = menu.getCloneExperience(menu.getSelectedClone());
            tooltip(extractor, mouseX, mouseY, Component.translatable(
                    "gui.chronoclones.anchor.experience.detail",
                    ExperienceStore.levelOf(points),
                    Math.round(ExperienceStore.progressOf(points) * 100),
                    points));
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
            tooltip(extractor, mouseX, mouseY, java.util.List.of(
                    Component.translatable(
                            reasonOf(menu.getFailureOrdinal()).translationKey(),
                            String.format("%+d, %+d, %+d", at.getX(), at.getY(), at.getZ())),
                    reportSummary()));
        } else if (transportAt(mouseX, mouseY) >= 0) {
            int control = transportAt(mouseX, mouseY);
            tooltip(extractor, mouseX, mouseY,
                    Component.translatable(RunState.byOrdinal(control).translationKey()));
        } else if (redstoneAt(mouseX, mouseY)) {
            tooltip(extractor, mouseX, mouseY, java.util.List.of(
                    Component.translatable(menu.isObeyingRedstone()
                            ? "gui.chronoclones.anchor.redstone.obey"
                            : "gui.chronoclones.anchor.redstone.ignore"),
                    Component.translatable("gui.chronoclones.anchor.redstone.tip")));
        } else if (within(mouseX, mouseY, Layout.MARGIN, Layout.TIMELINE_Y,
                Layout.TIMELINE_WIDTH, Layout.TIMELINE_HEIGHT) && menu.getLengthTicks() > 0) {
            tooltip(extractor, mouseX, mouseY, java.util.List.of(
                    Component.translatable("gui.chronoclones.anchor.timeline.tip",
                            menu.getLengthTicks() / 20, menu.getActionCount()),
                    reportSummary()));
        }
    }

    private Component reportSummary() {
        return Component.translatable("gui.chronoclones.anchor.report.summary",
                menu.getReportOk(), menu.getReportSkipped());
    }

    private void tooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY, Component text) {
        extractor.setTooltipForNextFrame(font, text, mouseX, mouseY);
    }

    private void tooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                         java.util.List<Component> lines) {
        extractor.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private boolean within(int mouseX, int mouseY, int x, int y, int width, int height) {
        int left = leftPos + x;
        int top = topPos + y;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private float chargeFraction() {
        return (float) menu.getCharge() / menu.getChargeCapacity();
    }

    private String text(String key) {
        return Component.translatable(key).getString();
    }

    private static int bannerEdge(DiagnosticState.FailureReason reason) {
        return reason.halts() ? AnchorPanels.HALTED : AnchorPanels.WARNING;
    }

    private static DiagnosticState.FailureReason reasonOf(int ordinal) {
        DiagnosticState.FailureReason[] values = DiagnosticState.FailureReason.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DiagnosticState.FailureReason.NONE;
    }
}
