package com.skilles.chronoclones.menu.client;

import java.util.List;

import com.skilles.chronoclones.item.RecordingDetail;
import com.skilles.chronoclones.network.RoutinePayloads;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.QuantityRule;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ActionSettings.TransferRule;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

/**
 * Every configurable action in a routine, and what to make of each one.
 *
 * <p>Not a container screen: there are no slots, and going through a menu would buy nothing but
 * ceremony. The routine arrives whole and every change is sent back one action at a time, so the
 * server never has to trust what this screen thinks the rest of the routine says.
 */
public class RoutineEditorScreen extends Screen {

    private static final int WIDTH = 340;
    private static final int HEIGHT = 236;
    private static final int MARGIN = 8;

    private static final int TITLE_Y = 7;

    private static final int TIMELINE_Y = 19;
    private static final int TIMELINE_HEIGHT = 26;

    private static final int PANEL_Y = 52;
    private static final int PANEL_BOTTOM = HEIGHT - 34;

    private static final int LIST_WIDTH = 150;
    private static final int ROW_HEIGHT = 22;
    private static final int ROWS_Y = PANEL_Y + 5;

    private static final int PANE_X = MARGIN + LIST_WIDTH + 6;
    private static final int PANE_WIDTH = WIDTH - MARGIN - PANE_X;

    /** The details pane: a two-line header, then rows of label + control. */
    private static final int HEADER_HEIGHT = 27;
    private static final int CONTROL_WIDTH = 100;
    private static final int CONTROL_HEIGHT = 16;
    private static final int CONTROL_ROW = CONTROL_HEIGHT + 3;

    private static final int BAR_Y = HEIGHT - 28;
    private static final int BAR_HEIGHT = 18;

    private final RoutinePayloads.Source source;

    private Recording routine;
    private int selected;
    private int scroll;

    private int left;
    private int top;

    /**
     * Typing is applied locally and sent when the name is done being typed - on selection change or
     * close - rather than one packet per keystroke. Any other control's edit carries the current
     * name with it, so pressing one also settles the debt.
     */
    private boolean nameDirty;

    /**
     * A discard is one click from losing a performance nobody can record again, so the button asks
     * once. Cleared whenever the selection moves, so an armed button cannot be forgotten about.
     */
    private boolean discardArmed;

    public RoutineEditorScreen(RoutinePayloads.Source source, Recording routine) {
        super(Component.translatable("gui.chronoclones.editor.title"));
        this.source = source;
        this.routine = routine;
    }

    private List<TimedAction> actions() {
        return routine.actions();
    }

    private int rowsVisible() {
        return (PANEL_BOTTOM - 4 - ROWS_Y) / ROW_HEIGHT;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;

        rebuildControls();
    }

    /**
     * The pane is rebuilt whenever the selection moves, because which controls belong there is a
     * question about the selected action rather than about the screen.
     */
    private void rebuildControls() {
        clearWidgets();
        addBottomBar();

        if (actions().isEmpty()) {
            return;
        }

        int x = left + PANE_X + PANE_WIDTH - 5 - CONTROL_WIDTH;
        int y = controlRowY(0);

        EditBox nameBox = new EditBox(font, x + 4, y + 4, CONTROL_WIDTH - 8, CONTROL_HEIGHT - 6,
                Component.translatable("gui.chronoclones.editor.label.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(48);
        nameBox.setValue(settings().name());
        nameBox.setHint(Component.translatable("gui.chronoclones.editor.name.hint"));
        nameBox.setResponder(this::rename);
        addRenderableWidget(nameBox);

        addControl(1, slotLabel(), () -> {
            SlotRule rule = settings().slot();
            SlotRule.Mode[] modes = SlotRule.Mode.values();
            apply(settings().withSlot(new SlotRule(
                    modes[(rule.mode().ordinal() + 1) % modes.length], rule.slot())));
        });

        if (isTargeted()) {
            addControl(2, completionLabel(), () -> {
                TargetRule rule = settings().target();
                apply(settings().withTarget(rule.withCompletion(
                        rule.completion() == TargetRule.Completion.ONCE
                                ? TargetRule.Completion.UNTIL_DEAD
                                : TargetRule.Completion.ONCE)));
            });
            addControl(3, stickyLabel(), () -> {
                TargetRule rule = settings().target();
                apply(settings().withTarget(rule.withSticky(!rule.sticky())));
            });
            addControl(4, targetLabel(), () -> {
                TargetRule rule = settings().target();
                apply(settings().withTarget(rule.withFilter(
                        rule.filter().isEmpty() ? List.of(recordedType()) : List.of())));
            });
            addRenderableWidget(new RadiusSlider(font, x, controlRowY(5),
                    CONTROL_WIDTH, CONTROL_HEIGHT, settings().target(),
                    radius -> apply(settings().withTarget(settings().target().withRadius(radius)))));
        } else if (action() instanceof ChronoAction.UseContainer session) {
            addControl(2, itemsLabel(), () -> {
                TransferRule rule = settings().transfer();
                apply(settings().withTransfer(rule.withItems(
                        rule.items().isEmpty() ? carriedItems(session) : List.of())));
            });
            addRenderableWidget(new QuantitySlider(font, x, controlRowY(3),
                    CONTROL_WIDTH, CONTROL_HEIGHT, settings().transfer().quantity(),
                    cap -> apply(settings().withTransfer(
                            settings().transfer().withQuantity(QuantityRule.atMost(cap))))));
        }
    }

    /** Cycling buttons rebuild the whole pane, so every label rereads the settings it shows. */
    private void addControl(int row, Component label, Runnable onPress) {
        int x = left + PANE_X + PANE_WIDTH - 5 - CONTROL_WIDTH;
        addRenderableWidget(new FlatButton(font, x, controlRowY(row), CONTROL_WIDTH, CONTROL_HEIGHT,
                label, () -> {
                    onPress.run();
                    rebuildControls();
                }));
    }

    private int controlRowY(int row) {
        return top + PANEL_Y + 5 + HEADER_HEIGHT + row * CONTROL_ROW;
    }

    /** Reordering is not built yet; the greyed buttons hold its place so the layout is honest. */
    private void addBottomBar() {
        String[] placeholders = {"+", "^", "v"};
        for (int i = 0; i < placeholders.length; i++) {
            FlatButton button = new FlatButton(font, left + MARGIN + i * 21, top + BAR_Y,
                    18, BAR_HEIGHT, Component.literal(placeholders[i]), () -> { });
            button.active = false;
            addRenderableWidget(button);
        }

        addRenderableWidget(new FlatButton(font, left + WIDTH - MARGIN - 110, top + BAR_Y,
                110, BAR_HEIGHT, discardLabel(), () -> {
            if (!discardArmed) {
                discardArmed = true;
                rebuildControls();
                return;
            }
            ClientPacketDistributor.sendToServer(new RoutinePayloads.Discard(source));
            onClose();
        }, true));
    }

    // ------------------------------------------------------------------ the selected action

    private TimedAction selectedAction() {
        return actions().get(Math.clamp(selected, 0, actions().size() - 1));
    }

    private ChronoAction action() {
        return selectedAction().action();
    }

    private ActionSettings settings() {
        return selectedAction().settings();
    }

    /** True for the action families that choose something to act on rather than a square. */
    private boolean isTargeted() {
        return action() instanceof ChronoAction.AttackEntity
                || action() instanceof ChronoAction.InteractEntity;
    }

    private Holder<net.minecraft.world.entity.EntityType<?>> recordedType() {
        return switch (action()) {
            case ChronoAction.AttackEntity a -> a.expectedType();
            case ChronoAction.InteractEntity a -> a.expectedType();
            default -> throw new IllegalStateException("not a targeted action");
        };
    }

    /** The items the session was recorded carrying, which is what "only these" means. */
    private static List<Holder<Item>> carriedItems(ChronoAction.UseContainer session) {
        return session.carrier().stream()
                .map(carried -> carried.stack().getItem())
                .distinct()
                .map(BuiltInRegistries.ITEM::wrapAsHolder)
                .toList();
    }

    /**
     * Applies a change locally and tells the server, which re-checks it rather than trusting us.
     */
    private void apply(ActionSettings settings) {
        routine = routine.withSettings(selected, settings);
        nameDirty = false;
        ClientPacketDistributor.sendToServer(
                new RoutinePayloads.EditAction(source, selected, settings));
    }

    private void rename(String text) {
        routine = routine.withSettings(selected, settings().withName(text));
        nameDirty = true;
    }

    private void flushName() {
        if (nameDirty) {
            apply(settings());
        }
    }

    @Override
    public void onClose() {
        flushName();
        super.onClose();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);

        AnchorPanels.panel(g, left - 2, top - 2, WIDTH + 4, HEIGHT + 4);
        g.fill(left, top, left + WIDTH, top + HEIGHT, AnchorPanels.WINDOW);

        g.text(font, title, left + MARGIN, top + TITLE_Y, AnchorPanels.TEXT);
        String count = Component.translatable("gui.chronoclones.editor.count",
                actions().size()).getString();
        g.text(font, count, left + WIDTH - MARGIN - font.width(count), top + TITLE_Y,
                AnchorPanels.MUTED);

        timeline(g);
        listPanel(g, mouseX, mouseY);
        detailsPane(g);
    }

    /**
     * The whole routine as a ruler: second marks, a label every few, a diamond per action.
     */
    private void timeline(GuiGraphicsExtractor g) {
        AnchorPanels.panel(g, left + MARGIN, top + TIMELINE_Y, WIDTH - MARGIN * 2, TIMELINE_HEIGHT);

        int x = left + MARGIN + 7;
        int width = WIDTH - MARGIN * 2 - 14;
        int trackY = top + TIMELINE_Y + TIMELINE_HEIGHT - 9;

        g.fill(x, trackY, x + width, trackY + 2, AnchorPanels.TRACK);

        int length = Math.max(routine.lengthTicks(), 1);
        int seconds = Math.max(length / 20, 1);
        int labelStep = Math.max(1, Math.round(seconds / 4.0f));

        for (int s = 0; s <= seconds; s++) {
            int at = x + (width - 1) * Math.min(s * 20, length) / length;
            boolean labelled = s % labelStep == 0 || s == seconds;
            g.fill(at, trackY - (labelled ? 4 : 2), at + 1, trackY, AnchorPanels.SLOT_EDGE);
            if (labelled) {
                String label = s + "s";
                g.text(font, label, Math.min(at - font.width(label) / 2, left + WIDTH - MARGIN - 6 - font.width(label)),
                        top + TIMELINE_Y + 4, AnchorPanels.MUTED);
            }
        }

        for (int i = 0; i < actions().size(); i++) {
            int at = x + (width - 1) * Math.clamp(actions().get(i).tick(), 0, length) / length;
            diamond(g, at, trackY + 1, i == selected ? 4 : 3,
                    i == selected ? AnchorPanels.TEXT : kindColour(actions().get(i).action()));
        }
    }

    private void listPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        AnchorPanels.panel(g, left + MARGIN, top + PANEL_Y, LIST_WIDTH, PANEL_BOTTOM - PANEL_Y);
        AnchorPanels.legend(g, font, Component.translatable("gui.chronoclones.editor.actions").getString(),
                left + MARGIN + 8, top + PANEL_Y);

        if (actions().isEmpty()) {
            g.text(font, Component.translatable("gui.chronoclones.editor.empty"),
                    left + MARGIN + 6, top + ROWS_Y + 4, AnchorPanels.MUTED);
            return;
        }

        g.enableScissor(left + MARGIN + 2, top + PANEL_Y + 2,
                left + MARGIN + LIST_WIDTH - 2, top + PANEL_BOTTOM - 2);
        for (int row = 0; row < rowsVisible(); row++) {
            int index = scroll + row;
            if (index >= actions().size()) {
                break;
            }
            drawRow(g, index, top + ROWS_Y + row * ROW_HEIGHT, mouseX, mouseY);
        }
        g.disableScissor();
    }

    private void drawRow(GuiGraphicsExtractor g, int index, int y, int mouseX, int mouseY) {
        int x = left + MARGIN;
        boolean current = index == selected;
        boolean hovered = mouseX >= x && mouseX < x + LIST_WIDTH
                && mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (current) {
            AnchorPanels.outline(g, x + 2, y, LIST_WIDTH - 4, ROW_HEIGHT - 1, AnchorPanels.ACCENT);
        } else if (hovered) {
            g.fill(x + 3, y + 1, x + LIST_WIDTH - 3, y + ROW_HEIGHT - 2, AnchorPanels.SLOT_EDGE);
        }

        TimedAction timed = actions().get(index);
        g.text(font, timed.tick() / 20 + "s", x + 6, y + 3, AnchorPanels.MUTED);
        diamond(g, x + 30, y + 6, 3, kindColour(timed.action()));

        int textX = x + 38;
        int textWidth = LIST_WIDTH - 44;
        g.text(font, font.plainSubstrByWidth(rowTitle(timed), textWidth), textX, y + 3,
                current ? AnchorPanels.ACCENT : AnchorPanels.TEXT);
        g.text(font, font.plainSubstrByWidth(summaryOf(timed), textWidth), textX, y + 12,
                AnchorPanels.MUTED);
    }

    private void detailsPane(GuiGraphicsExtractor g) {
        AnchorPanels.panel(g, left + PANE_X, top + PANEL_Y, PANE_WIDTH, PANEL_BOTTOM - PANEL_Y);
        AnchorPanels.legend(g, font, Component.translatable("gui.chronoclones.editor.details").getString(),
                left + PANE_X + 8, top + PANEL_Y);

        if (actions().isEmpty()) {
            return;
        }

        int x = left + PANE_X + 6;
        int y = top + PANEL_Y + 6;
        int inner = PANE_WIDTH - 12;

        g.text(font, font.plainSubstrByWidth(rowTitle(selectedAction()), inner), x, y,
                AnchorPanels.ACCENT);
        g.text(font, font.plainSubstrByWidth(summaryOf(selectedAction()), inner), x, y + 10,
                AnchorPanels.MUTED);
        g.fill(x, y + 21, x + inner, y + 22, AnchorPanels.SLOT_EDGE);

        label(g, 0, "gui.chronoclones.editor.label.name");
        AnchorPanels.track(g, left + PANE_X + PANE_WIDTH - 5 - CONTROL_WIDTH, controlRowY(0),
                CONTROL_WIDTH, CONTROL_HEIGHT);
        label(g, 1, "gui.chronoclones.editor.label.slot");

        if (isTargeted()) {
            label(g, 2, "gui.chronoclones.editor.label.finish");
            label(g, 3, "gui.chronoclones.editor.label.sticky");
            label(g, 4, "gui.chronoclones.editor.label.target");
            label(g, 5, "gui.chronoclones.editor.label.radius");
        } else if (action() instanceof ChronoAction.UseContainer) {
            label(g, 2, "gui.chronoclones.editor.label.items");
            label(g, 3, "gui.chronoclones.editor.label.amount");
        }
    }

    private void label(GuiGraphicsExtractor g, int row, String key) {
        g.text(font, Component.translatable(key), left + PANE_X + 6,
                controlRowY(row) + (CONTROL_HEIGHT - font.lineHeight) / 2 + 1,
                AnchorPanels.MUTED);
    }

    /** A small diamond, the timeline's and the list's shared mark for one action. */
    private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int size, int colour) {
        for (int row = 0; row < size; row++) {
            g.fill(cx - row, cy - size + 1 + row, cx + row + 1, cy - size + 2 + row, colour);
            g.fill(cx - row, cy + size - 1 - row, cx + row + 1, cy + size - row, colour);
        }
    }

    private static int kindColour(ChronoAction action) {
        return switch (action.type()) {
            case USE_CONTAINER -> AnchorPanels.WARNING;
            case ATTACK_ENTITY, INTERACT_ENTITY -> AnchorPanels.ACCENT;
            default -> AnchorPanels.TEXT;
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubled) {
        int row = rowAt((int) event.x(), (int) event.y());
        if (row >= 0 && row != selected) {
            flushName();
            selected = row;
            discardArmed = false;
            rebuildControls();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int overflow = actions().size() - rowsVisible();
        if (overflow > 0) {
            scroll = Math.clamp(scroll - (int) Math.signum(deltaY), 0, overflow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    /** Which row a screen coordinate lands on, or -1. */
    private int rowAt(int x, int y) {
        if (x < left + MARGIN || x >= left + MARGIN + LIST_WIDTH || y < top + ROWS_Y) {
            return -1;
        }
        int row = (y - (top + ROWS_Y)) / ROW_HEIGHT;
        int index = scroll + row;
        return row < rowsVisible() && index < actions().size() ? index : -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ labels

    /** The player's name for the action, or the short name of its kind. */
    private String rowTitle(TimedAction timed) {
        return timed.settings().hasName()
                ? timed.settings().name()
                : Component.translatable(typeKey(timed.action())).getString();
    }

    private String summaryOf(TimedAction timed) {
        return RecordingDetail.summary(timed.action()).getString();
    }

    private static String typeKey(ChronoAction action) {
        return "gui.chronoclones.editor.type." + action.type().getSerializedName();
    }

    private Component slotLabel() {
        SlotRule rule = settings().slot();
        if (rule.mode() == SlotRule.Mode.ANY || rule.slot() < 0) {
            return Component.translatable("gui.chronoclones.editor.slot.any");
        }
        return Component.translatable(rule.mode() == SlotRule.Mode.EXACT
                ? "gui.chronoclones.editor.slot.exact"
                : "gui.chronoclones.editor.slot.prefer", rule.slot());
    }

    private Component completionLabel() {
        return Component.translatable("gui.chronoclones.editor.completion."
                + settings().target().completion().getSerializedName());
    }

    private Component stickyLabel() {
        return Component.translatable(settings().target().sticky()
                ? "gui.chronoclones.editor.sticky.on"
                : "gui.chronoclones.editor.sticky.off");
    }

    private Component targetLabel() {
        return settings().target().filter().isEmpty()
                ? Component.translatable("gui.chronoclones.editor.filter.any")
                : recordedType().value().getDescription();
    }

    private Component itemsLabel() {
        List<Holder<Item>> items = settings().transfer().items();
        return items.isEmpty()
                ? Component.translatable("gui.chronoclones.editor.items.any")
                : Component.translatable("gui.chronoclones.editor.items.only", items.size());
    }

    private Component discardLabel() {
        return Component.translatable(discardArmed
                ? "gui.chronoclones.editor.discard.confirm"
                : "gui.chronoclones.editor.discard");
    }

}
