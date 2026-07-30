package com.skilles.chronoclones.menu.client;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.item.RecordingDetail;
import com.skilles.chronoclones.network.RoutinePayloads;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.QuantityRule;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.StepSettings;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ActionSettings.TransferRule;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.SessionStep;
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
 *
 * <p>A container session is a list of steps rather than one thing, so the list is a tree: the
 * selected session opens to show its steps, each addressable on its own.
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
    /** How far a step's row is pushed in from its session's, so the tree reads as one. */
    private static final int STEP_INDENT = 10;

    private static final int PANE_X = MARGIN + LIST_WIDTH + 6;
    private static final int PANE_WIDTH = WIDTH - MARGIN - PANE_X;

    /** The details pane: a two-line header, then rows of label + control. */
    private static final int HEADER_HEIGHT = 27;
    private static final int CONTROL_WIDTH = 100;
    private static final int CONTROL_HEIGHT = 16;
    private static final int CONTROL_ROW = CONTROL_HEIGHT + 3;

    private static final int BAR_Y = HEIGHT - 28;
    private static final int BAR_HEIGHT = 18;

    /** The discard control, beside the title: small, because the bottom bar is for the routine. */
    private static final int DISCARD_WIDTH = 92;

    private final RoutinePayloads.Source source;

    private Recording routine;

    /** The selected row. A negative step means the action itself rather than one of its steps. */
    private int selected;
    private int selectedStep = -1;
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

    // ------------------------------------------------------------------ the tree

    /**
     * One line of the list: an action, or one step of it.
     */
    private record Row(int action, int step) {

        boolean isStep() {
            return step >= 0;
        }
    }

    /**
     * The visible lines, with the selected session opened out.
     *
     * <p>Only the selected one: a session of forty clicks would otherwise bury every action after it,
     * and expanding what you just selected needs no state of its own to remember.
     */
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < actions().size(); index++) {
            rows.add(new Row(index, -1));
            if (index == selected && steps(index) != null) {
                for (int step = 0; step < steps(index).size(); step++) {
                    rows.add(new Row(index, step));
                }
            }
        }
        return rows;
    }

    /** The steps of an action, or null if it is not a session. */
    private @org.jspecify.annotations.Nullable List<SessionStep> steps(int index) {
        return actions().get(index).action() instanceof ChronoAction.UseContainer session
                ? session.steps()
                : null;
    }

    private Row selectedRow() {
        return new Row(Math.clamp(selected, 0, Math.max(0, actions().size() - 1)), selectedStep);
    }

    // ------------------------------------------------------------------ controls

    /**
     * The pane is rebuilt whenever the selection moves, because which controls belong there is a
     * question about the selected row rather than about the screen.
     */
    private void rebuildControls() {
        clearWidgets();
        addTitleBar();
        addBottomBar();

        if (actions().isEmpty()) {
            return;
        }
        if (selectedRow().isStep()) {
            addStepControls();
        } else {
            addActionControls();
        }
    }

    private int controlX() {
        return left + PANE_X + PANE_WIDTH - 5 - CONTROL_WIDTH;
    }

    private void addActionControls() {
        addName(settings().name(), this::renameAction);
        addControl(1, slotLabel(settings().slot()), () -> apply(settings().withSlot(
                cycled(settings().slot()))));

        if (isTargeted()) {
            addTargetControls(2);
            return;
        }
        if (action() instanceof ChronoAction.UseContainer session) {
            // A session's own settings are about reaching it, not about what it does inside: what it
            // does inside is the steps, each with settings of its own.
            if (session.target() instanceof MenuTarget.Entity) {
                addTargetControls(2);
            }
        }
    }

    /** Finish, sticky, filter and radius: how an action chooses what to act on. */
    private void addTargetControls(int firstRow) {
        addControl(firstRow, completionLabel(), () -> {
            TargetRule rule = settings().target();
            apply(settings().withTarget(rule.withCompletion(
                    rule.completion() == TargetRule.Completion.ONCE
                            ? TargetRule.Completion.UNTIL_DEAD
                            : TargetRule.Completion.ONCE)));
        });
        addControl(firstRow + 1, stickyLabel(), () -> {
            TargetRule rule = settings().target();
            apply(settings().withTarget(rule.withSticky(!rule.sticky())));
        });
        addControl(firstRow + 2, targetLabel(), () -> {
            TargetRule rule = settings().target();
            apply(settings().withTarget(rule.withFilter(
                    rule.filter().isEmpty() ? List.of(recordedType()) : List.of())));
        });
        addRenderableWidget(new RadiusSlider(font, controlX(), controlRowY(firstRow + 3),
                CONTROL_WIDTH, CONTROL_HEIGHT, settings().target(),
                radius -> apply(settings().withTarget(settings().target().withRadius(radius)))));
    }

    /**
     * A step answers only what its kind can be asked: a move has squares and amounts, a trade or a
     * rename is one indivisible thing, and a click nobody could name has nothing to say at all.
     */
    private void addStepControls() {
        StepSettings step = stepSettings();
        addControl(0, enabledLabel(step), () -> applyStep(step.withEnabled(!step.enabled())));

        if (!(selectedStep() instanceof SessionStep.Move move)) {
            if (selectedStep() instanceof SessionStep.RawClick) {
                return;
            }
            addName(step.name(), this::renameStep);
            return;
        }

        addControl(1, slotLabel(step.slot()), () -> applyStep(step.withSlot(cycled(step.slot()))));
        addControl(2, stepItemsLabel(step), () -> {
            TransferRule rule = step.transfer();
            applyStep(step.withTransfer(rule.withItems(
                    rule.items().isEmpty() ? List.of(move.item()) : List.of())));
        });
        addRenderableWidget(new QuantitySlider(font, controlX(), controlRowY(3),
                CONTROL_WIDTH, CONTROL_HEIGHT, step.transfer().quantity(),
                cap -> applyStep(stepSettings().withTransfer(
                        stepSettings().transfer().withQuantity(QuantityRule.atMost(cap))))));
    }

    private void addName(String value, java.util.function.Consumer<String> onType) {
        int y = controlRowY(nameRow());
        EditBox nameBox = new EditBox(font, controlX() + 4, y + 4, CONTROL_WIDTH - 8,
                CONTROL_HEIGHT - 6, Component.translatable("gui.chronoclones.editor.label.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(48);
        nameBox.setValue(value);
        nameBox.setHint(Component.translatable("gui.chronoclones.editor.name.hint"));
        nameBox.setResponder(onType::accept);
        addRenderableWidget(nameBox);
    }

    /** A step's first row is its switch, so its name sits below; an action's name comes first. */
    private int nameRow() {
        return selectedRow().isStep() ? 1 : 0;
    }

    /** Cycling buttons rebuild the whole pane, so every label rereads the settings it shows. */
    private void addControl(int row, Component label, Runnable onPress) {
        addRenderableWidget(new FlatButton(font, controlX(), controlRowY(row), CONTROL_WIDTH,
                CONTROL_HEIGHT, label, () -> {
                    onPress.run();
                    rebuildControls();
                }));
    }

    private int controlRowY(int row) {
        return top + PANEL_Y + 5 + HEADER_HEIGHT + row * CONTROL_ROW;
    }

    private static SlotRule cycled(SlotRule rule) {
        SlotRule.Mode[] modes = SlotRule.Mode.values();
        return new SlotRule(modes[(rule.mode().ordinal() + 1) % modes.length], rule.slot());
    }

    /**
     * Discarding the whole routine sits beside the title, away from the bar that acts on one action.
     */
    private void addTitleBar() {
        addRenderableWidget(new FlatButton(font, left + WIDTH - MARGIN - DISCARD_WIDTH,
                top + TITLE_Y - 4, DISCARD_WIDTH, CONTROL_HEIGHT - 2, discardLabel(), () -> {
            if (!discardArmed) {
                discardArmed = true;
                rebuildControls();
                return;
            }
            ClientPacketDistributor.sendToServer(new RoutinePayloads.Discard(source));
            onClose();
        }, true));
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

        FlatButton delete = new FlatButton(font, left + WIDTH - MARGIN - 110, top + BAR_Y,
                110, BAR_HEIGHT, Component.translatable("gui.chronoclones.editor.delete"), () -> {
            flushName();
            int index = selectedRow().action();
            ClientPacketDistributor.sendToServer(new RoutinePayloads.RemoveAction(source, index));
            routine = routine.without(index);
            selected = Math.clamp(index, 0, Math.max(0, actions().size() - 1));
            selectedStep = -1;
            scroll = 0;
            rebuildControls();
        });
        // Nothing to delete, and nothing to select either.
        delete.active = !actions().isEmpty();
        addRenderableWidget(delete);
    }

    // ------------------------------------------------------------------ the selected row

    private TimedAction selectedAction() {
        return actions().get(selectedRow().action());
    }

    private ChronoAction action() {
        return selectedAction().action();
    }

    private ActionSettings settings() {
        return selectedAction().settings();
    }

    private SessionStep selectedStep() {
        return steps(selectedRow().action()).get(selectedRow().step());
    }

    private StepSettings stepSettings() {
        return settings().step(selectedRow().step());
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
            case ChronoAction.UseContainer a when a.target() instanceof MenuTarget.Entity entity ->
                    entity.expectedType();
            default -> throw new IllegalStateException("not a targeted action");
        };
    }

    /**
     * Applies a change locally and tells the server, which re-checks it rather than trusting us.
     */
    private void apply(ActionSettings settings) {
        routine = routine.withSettings(selectedRow().action(), settings);
        nameDirty = false;
        ClientPacketDistributor.sendToServer(
                new RoutinePayloads.EditAction(source, selectedRow().action(), settings));
    }

    /** The same, for one step: the whole action's settings still travel, with the step inside. */
    private void applyStep(StepSettings step) {
        apply(settings().withStep(selectedRow().step(), step));
    }

    private void renameAction(String text) {
        routine = routine.withSettings(selectedRow().action(), settings().withName(text));
        nameDirty = true;
    }

    private void renameStep(String text) {
        routine = routine.withSettings(selectedRow().action(),
                settings().withStep(selectedRow().step(), stepSettings().withName(text)));
        nameDirty = true;
    }

    private void flushName() {
        if (nameDirty && !actions().isEmpty()) {
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
        g.text(font, count, left + WIDTH - MARGIN - DISCARD_WIDTH - 6 - font.width(count),
                top + TITLE_Y, AnchorPanels.MUTED);

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

        List<Row> rows = rows();
        g.enableScissor(left + MARGIN + 2, top + PANEL_Y + 2,
                left + MARGIN + LIST_WIDTH - 2, top + PANEL_BOTTOM - 2);
        for (int line = 0; line < rowsVisible(); line++) {
            int index = scroll + line;
            if (index >= rows.size()) {
                break;
            }
            drawRow(g, rows.get(index), top + ROWS_Y + line * ROW_HEIGHT, mouseX, mouseY);
        }
        g.disableScissor();
    }

    private void drawRow(GuiGraphicsExtractor g, Row row, int y, int mouseX, int mouseY) {
        int x = left + MARGIN + (row.isStep() ? STEP_INDENT : 0);
        int width = LIST_WIDTH - (row.isStep() ? STEP_INDENT : 0);
        boolean current = row.equals(selectedRow());
        boolean hovered = mouseX >= x && mouseX < left + MARGIN + LIST_WIDTH
                && mouseY >= y && mouseY < y + ROW_HEIGHT;

        if (current) {
            AnchorPanels.outline(g, x + 2, y, width - 4, ROW_HEIGHT - 1, AnchorPanels.ACCENT);
        } else if (hovered) {
            g.fill(x + 3, y + 1, x + width - 3, y + ROW_HEIGHT - 2, AnchorPanels.SLOT_EDGE);
        }

        TimedAction timed = actions().get(row.action());
        if (row.isStep()) {
            drawStepRow(g, row, timed, x, y, width, current);
            return;
        }

        g.text(font, timed.tick() / 20 + "s", x + 6, y + 3, AnchorPanels.MUTED);
        diamond(g, x + 30, y + 6, 3, kindColour(timed.action()));

        int textX = x + 38;
        int textWidth = width - 44;
        g.text(font, font.plainSubstrByWidth(rowTitle(timed), textWidth), textX, y + 3,
                current ? AnchorPanels.ACCENT : AnchorPanels.TEXT);
        g.text(font, font.plainSubstrByWidth(summaryOf(timed), textWidth), textX, y + 12,
                AnchorPanels.MUTED);
    }

    private void drawStepRow(GuiGraphicsExtractor g, Row row, TimedAction timed,
                             int x, int y, int width, boolean current) {
        SessionStep step = steps(row.action()).get(row.step());
        StepSettings rule = timed.settings().step(row.step());

        // The tree's elbow, so a step reads as belonging to the session above it.
        g.fill(x - 5, y + 1, x - 4, y + ROW_HEIGHT - 2, AnchorPanels.SLOT_EDGE);
        g.fill(x - 5, y + 10, x, y + 11, AnchorPanels.SLOT_EDGE);

        int text = rule.enabled()
                ? (current ? AnchorPanels.ACCENT : AnchorPanels.TEXT)
                : AnchorPanels.MUTED;
        int textWidth = width - 12;

        g.text(font, font.plainSubstrByWidth(stepTitle(step, rule), textWidth), x + 6, y + 3, text);
        g.text(font, font.plainSubstrByWidth(RecordingDetail.stepLine(step).getString(), textWidth),
                x + 6, y + 12, AnchorPanels.MUTED);
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

        boolean isStep = selectedRow().isStep();
        String heading = isStep
                ? stepTitle(selectedStep(), stepSettings())
                : rowTitle(selectedAction());
        String detail = isStep
                ? RecordingDetail.stepLine(selectedStep()).getString()
                : summaryOf(selectedAction());

        g.text(font, font.plainSubstrByWidth(heading, inner), x, y, AnchorPanels.ACCENT);
        g.text(font, font.plainSubstrByWidth(detail, inner), x, y + 10, AnchorPanels.MUTED);
        g.fill(x, y + 21, x + inner, y + 22, AnchorPanels.SLOT_EDGE);

        if (isStep) {
            drawStepLabels(g);
            return;
        }

        label(g, 0, "gui.chronoclones.editor.label.name");
        nameTrack(g, 0);
        label(g, 1, "gui.chronoclones.editor.label.slot");

        if (isTargeted() || action() instanceof ChronoAction.UseContainer session
                && session.target() instanceof MenuTarget.Entity) {
            label(g, 2, "gui.chronoclones.editor.label.finish");
            label(g, 3, "gui.chronoclones.editor.label.sticky");
            label(g, 4, "gui.chronoclones.editor.label.target");
            label(g, 5, "gui.chronoclones.editor.label.radius");
        }
    }

    private void drawStepLabels(GuiGraphicsExtractor g) {
        label(g, 0, "gui.chronoclones.editor.label.enabled");

        if (selectedStep() instanceof SessionStep.Move) {
            label(g, 1, "gui.chronoclones.editor.label.slot");
            label(g, 2, "gui.chronoclones.editor.label.items");
            label(g, 3, "gui.chronoclones.editor.label.amount");
            return;
        }
        if (selectedStep() instanceof SessionStep.RawClick) {
            return;
        }
        label(g, 1, "gui.chronoclones.editor.label.name");
        nameTrack(g, 1);
    }

    /** The recess a name is typed into, which an EditBox does not draw for itself. */
    private void nameTrack(GuiGraphicsExtractor g, int row) {
        AnchorPanels.track(g, controlX(), controlRowY(row), CONTROL_WIDTH, CONTROL_HEIGHT);
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
        Row row = rowAt((int) event.x(), (int) event.y());
        if (row != null && !row.equals(selectedRow())) {
            flushName();
            selected = row.action();
            selectedStep = row.step();
            discardArmed = false;
            rebuildControls();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int overflow = rows().size() - rowsVisible();
        if (overflow > 0) {
            scroll = Math.clamp(scroll - (int) Math.signum(deltaY), 0, overflow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    /** Which row a screen coordinate lands on, or null. */
    private @org.jspecify.annotations.Nullable Row rowAt(int x, int y) {
        if (x < left + MARGIN || x >= left + MARGIN + LIST_WIDTH || y < top + ROWS_Y) {
            return null;
        }
        int line = (y - (top + ROWS_Y)) / ROW_HEIGHT;
        int index = scroll + line;
        List<Row> rows = rows();
        return line < rowsVisible() && index < rows.size() ? rows.get(index) : null;
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

    /** The player's name for the step, or the short name of its kind. */
    private String stepTitle(SessionStep step, StepSettings rule) {
        return rule.hasName()
                ? rule.name()
                : Component.translatable("gui.chronoclones.editor.step."
                        + step.kind().getSerializedName()).getString();
    }

    private String summaryOf(TimedAction timed) {
        return RecordingDetail.summary(timed.action()).getString();
    }

    private static String typeKey(ChronoAction action) {
        return "gui.chronoclones.editor.type." + action.type().getSerializedName();
    }

    private Component slotLabel(SlotRule rule) {
        if (rule.mode() == SlotRule.Mode.ANY) {
            return Component.translatable("gui.chronoclones.editor.slot.any");
        }
        if (rule.slot() < 0) {
            // A step's square comes from the move rather than from the rule, so there is no number
            // to show; the mode is still the whole question.
            return Component.translatable(rule.mode() == SlotRule.Mode.EXACT
                    ? "gui.chronoclones.editor.slot.recorded_only"
                    : "gui.chronoclones.editor.slot.recorded_first");
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

    private Component stepItemsLabel(StepSettings step) {
        List<Holder<Item>> items = step.transfer().items();
        return items.isEmpty()
                ? Component.translatable("gui.chronoclones.editor.items.any")
                : Component.translatable(items.getFirst().value().getDescriptionId());
    }

    private Component enabledLabel(StepSettings step) {
        return Component.translatable(step.enabled()
                ? "gui.chronoclones.editor.enabled.on"
                : "gui.chronoclones.editor.enabled.off");
    }

    private Component discardLabel() {
        return Component.translatable(discardArmed
                ? "gui.chronoclones.editor.discard.confirm"
                : "gui.chronoclones.editor.discard");
    }
}
