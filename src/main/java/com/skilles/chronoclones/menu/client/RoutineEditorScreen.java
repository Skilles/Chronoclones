package com.skilles.chronoclones.menu.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
//? if >=26 {
import net.minecraft.client.input.KeyEvent;
//?}
//? if >=26 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.skilles.chronoclones.platform.PlatformClientNetwork;
import org.jspecify.annotations.NonNull;

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
    private static final int STEP_INDENT = 10;

    private static final int PANE_X = MARGIN + LIST_WIDTH + 6;
    private static final int PANE_WIDTH = WIDTH - MARGIN - PANE_X;

    private static final int HEADER_HEIGHT = 27;
    private static final int CONTROL_WIDTH = 100;
    private static final int CONTROL_HEIGHT = 16;
    private static final int CONTROL_ROW = CONTROL_HEIGHT + 3;

    private static final int BAR_Y = HEIGHT - 28;
    private static final int BAR_HEIGHT = 18;

    private static final int DISCARD_WIDTH = 92;

    private final RoutinePayloads.Source source;

    private Recording routine;

    private int selected;
    private int selectedStep = -1;
    private int scroll;

    private int left;
    private int top;

    private boolean nameDirty;

    private boolean discardArmed;

    // Counted up in step with the server: each accepted edit bumps both ends by one, so a
    // mismatch means somebody else changed the routine and this editor is talking about the
    // wrong actions. The server refuses such edits and re-opens the editor on current state.
    private int revision;

    public RoutineEditorScreen(RoutinePayloads.Source source, Recording routine, int revision) {
        super(Component.translatable("gui.chronoclones.editor.title"));
        this.source = source;
        this.routine = routine;
        this.revision = revision;
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

    private record Row(int action, int step) {

        boolean isStep() {
            return step >= 0;
        }
    }

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

    private @org.jspecify.annotations.Nullable List<SessionStep> steps(int index) {
        return actions().get(index).action() instanceof ChronoAction.UseContainer session
                ? session.steps()
                : null;
    }

    private Row selectedRow() {
        return new Row(Math.clamp(selected, 0, Math.max(0, actions().size() - 1)), selectedStep);
    }

    private void revealSelection() {
        List<Row> rows = rows();
        int index = rows.indexOf(selectedRow());

        if (index >= 0) {
            if (index < scroll) {
                scroll = index;
            }
            List<SessionStep> steps = selectedRow().isStep() ? null : steps(selectedRow().action());
            int last = index + (steps == null ? 0 : steps.size());
            if (last >= scroll + rowsVisible()) {
                scroll = Math.min(last - rowsVisible() + 1, index);
            }
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - rowsVisible()));
    }

    private void rebuildControls() {
        revealSelection();
        clearWidgets();
        rows.clear();
        hasNameBox = false;
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

        int row = 1;
        if (swingsSomething()) {
            addControl(row++, "tool", "tool", toolLabel(),
                    () -> apply(settings().withTool(nextTool(settings().tool()))));
        }
        if (takesAnItem() && choosesItsOwnSquare()) {
            addControl(row++, "slot", slotHelp(), slotLabel(settings().slot()),
                    () -> apply(settings().withSlot(cycled(settings().slot()))));
        }
        if (carriesComponents()) {
            addControl(row++, "components", "components", componentsLabel(),
                    () -> apply(settings().withItem(
                            settings().item() == ActionSettings.ItemRule.EXACT
                                    ? ActionSettings.ItemRule.SAME_ITEM
                                    : ActionSettings.ItemRule.EXACT)));
        }
        if (hasASubject()) {
            addControl(row++, subjectOption(), subjectOption(), subjectValue(),
                    () -> apply(settings().withRecordedSubject(!settings().recordedSubject())));
        }
        if (action() instanceof ChronoAction.AttackEntity) {
            addControl(row++, "finish", "finish", completionLabel(), () -> {
                TargetRule rule = settings().target();
                apply(settings().withTarget(rule.withCompletion(
                        rule.completion() == TargetRule.Completion.ONCE
                                ? TargetRule.Completion.UNTIL_DEAD
                                : TargetRule.Completion.ONCE)));
            });
        }
        if (action() instanceof ChronoAction.UseContainer session) {
            row = addCarrierControls(session, row);
        }
        if (locksItsTarget()) {
            addCheckbox(row++, "gui.chronoclones.editor.label.sticky", settings().target().sticky(),
                    on -> apply(settings().withTarget(settings().target().withSticky(on))));
        }
        if (looksForATarget()) {
            addSlider(row++, "radius", "radius", new RadiusSlider(font, controlX(), controlRowY(row - 1),
                    CONTROL_WIDTH, CONTROL_HEIGHT, settings().target(),
                    radius -> apply(settings().withTarget(settings().target().withRadius(radius)))));
        }
    }

    private int addCarrierControls(ChronoAction.UseContainer session, int firstRow) {
        if (session.carrier().isEmpty()) {
            return firstRow;
        }
        addControl(firstRow, "items", "items", itemsLabel(), () -> {
            TransferRule rule = settings().transfer();
            apply(settings().withTransfer(rule.withItems(
                    rule.items().isEmpty() ? carriedItems(session) : List.of())));
        });
        addSlider(firstRow + 1, "quantity", "quantity", new QuantitySlider(font, controlX(),
                controlRowY(firstRow + 1), CONTROL_WIDTH, CONTROL_HEIGHT,
                settings().transfer().quantity(),
                cap -> apply(settings().withTransfer(
                        settings().transfer().withQuantity(QuantityRule.atMost(cap))))));
        return firstRow + 2;
    }

    private boolean hasASubject() {
        return action() instanceof ChronoAction.BreakBlock
                || action() instanceof ChronoAction.PlaceBlock
                || action() instanceof ChronoAction.UseOnBlock use
                        && use.expectedBlock().isPresent()
                || action() instanceof ChronoAction.AttackEntity
                || action() instanceof ChronoAction.InteractEntity
                || action() instanceof ChronoAction.UseContainer session
                        && session.target() instanceof MenuTarget.Entity;
    }

    private boolean choosesItsOwnSquare() {
        return !swingsSomething() || settings().tool() == ActionSettings.ToolRule.EXACT;
    }

    private boolean swingsSomething() {
        return action() instanceof ChronoAction.BreakBlock
                || action() instanceof ChronoAction.AttackEntity;
    }

    private boolean carriesComponents() {
        return switch (action()) {
            case ChronoAction.UseOnBlock a -> a.itemTemplate().hasComponents();
            case ChronoAction.UseItem a -> a.itemTemplate().hasComponents();
            case ChronoAction.InteractEntity a -> a.itemTemplate().hasComponents();
            case ChronoAction.PlaceBlock a -> a.itemTemplate().hasComponents();
            default -> false;
        };
    }

    private Component componentsLabel() {
        return Component.translatable(settings().item() == ActionSettings.ItemRule.EXACT
                ? "gui.chronoclones.editor.components.exact"
                : "gui.chronoclones.editor.components.same_item");
    }

    private static ActionSettings.ToolRule nextTool(ActionSettings.ToolRule rule) {
        ActionSettings.ToolRule[] rules = ActionSettings.ToolRule.values();
        return rules[(rule.ordinal() + 1) % rules.length];
    }

    private static Component slotName(int slot) {
        if (slot < 0) {
            return Component.translatable("gui.chronoclones.editor.slot.unrecorded");
        }
        if (slot < HOTBAR_SIZE) {
            return Component.translatable("gui.chronoclones.editor.slot.hotbar", slot + 1);
        }
        int stored = slot - HOTBAR_SIZE;
        return Component.translatable("gui.chronoclones.editor.slot.stored",
                stored / HOTBAR_SIZE + 1, stored % HOTBAR_SIZE + 1);
    }

    private static final int HOTBAR_SIZE = 9;

    private String slotHelp() {
        if (action() instanceof ChronoAction.BreakBlock) {
            return "slot.tool";
        }
        return action() instanceof ChronoAction.AttackEntity ? "slot.weapon" : "slot.item";
    }

    private boolean takesAnItem() {
        return switch (action()) {
            case ChronoAction.BreakBlock ignored -> true;
            case ChronoAction.PlaceBlock ignored -> true;
            case ChronoAction.UseOnBlock ignored -> true;
            case ChronoAction.UseItem ignored -> true;
            case ChronoAction.InteractEntity ignored -> true;
            case ChronoAction.AttackEntity ignored -> true;
            case ChronoAction.UseContainer ignored -> false;
        };
    }

    private boolean locksItsTarget() {
        return action() instanceof ChronoAction.AttackEntity
                && settings().target().completion() == TargetRule.Completion.ONCE;
    }

    private boolean looksForATarget() {
        return action() instanceof ChronoAction.AttackEntity
                || action() instanceof ChronoAction.InteractEntity
                || action() instanceof ChronoAction.UseContainer session
                        && session.target() instanceof MenuTarget.Entity;
    }

    private static List<Holder<Item>> carriedItems(ChronoAction.UseContainer session) {
        return session.carrier().stream()
                .map(carried -> carried.stack().getItem())
                .distinct()
                .map(BuiltInRegistries.ITEM::wrapAsHolder)
                .toList();
    }

    private void addStepControls() {
        StepSettings step = stepSettings();
        addCheckbox(0, "gui.chronoclones.editor.label.enabled", step.enabled(),
                on -> applyStep(stepSettings().withEnabled(on)));

        if (!(selectedStep() instanceof SessionStep.Move move)) {
            addName(step.name(), this::renameStep);
            return;
        }

        addControl(1, "slot", "slot.menu", slotLabel(step.slot()),
                () -> applyStep(stepSettings().withSlot(cycled(stepSettings().slot()))));
        addControl(2, "item", "item", stepItemsLabel(step), () -> applyStep(stepSettings().withItems(
                stepSettings().items().isEmpty() ? List.of(move.item()) : List.of())));
        addControl(3, "amount", "amount", amountLabel(step, move),
                () -> applyStep(stepSettings().withAmount(nextAmount(stepSettings().amount()))));
    }

    private static Optional<SessionStep.Amount> nextAmount(Optional<SessionStep.Amount> amount) {
        SessionStep.Amount[] amounts = SessionStep.Amount.values();
        if (amount.isEmpty()) {
            return Optional.of(amounts[0]);
        }
        int next = amount.get().ordinal() + 1;
        return next < amounts.length ? Optional.of(amounts[next]) : Optional.empty();
    }

    private boolean hasNameBox;

    private boolean hasName() {
        return hasNameBox;
    }

    private void addName(String value, java.util.function.Consumer<String> onType) {
        hasNameBox = true;
        rows.add(new Labelled(nameRow(), "gui.chronoclones.editor.label.name", "name"));
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

    private int nameRow() {
        return selectedRow().isStep() ? 1 : 0;
    }

    private void addControl(int row, String option, String help, Component label, Runnable onPress) {
        FlatButton button = new FlatButton(font, controlX(), controlRowY(row), CONTROL_WIDTH,
                CONTROL_HEIGHT, label, () -> {
                    onPress.run();
                    rebuildControls();
                });
        button.setTooltip(explain(help));
        addRenderableWidget(button);
        rows.add(new Labelled(row, "gui.chronoclones.editor.label." + option, option));
    }

    private void addCheckbox(int row, String labelKey, boolean value, java.util.function.Consumer<Boolean> onToggle) {
        String option = labelKey.substring(labelKey.lastIndexOf('.') + 1);
        FlatCheckbox box = new FlatCheckbox(font, controlX(), controlRowY(row), CONTROL_WIDTH,
                CONTROL_HEIGHT, Component.translatable(checkboxKey(option, value)),
                () -> value, on -> {
                    onToggle.accept(on);
                    rebuildControls();
                });
        box.setTooltip(explain(option));
        addRenderableWidget(box);
        rows.add(new Labelled(row, labelKey, option));
    }

    private void addSlider(int row, String option, String help, FlatSlider slider) {
        slider.setTooltip(explain(help));
        addRenderableWidget(slider);
        rows.add(new Labelled(row, "gui.chronoclones.editor.label." + option, option));
    }

    private static Tooltip explain(String option) {
        return Tooltip.create(Component.translatable("gui.chronoclones.editor.help." + option));
    }

    private record Labelled(int row, String labelKey, String option) {}

    private final List<Labelled> rows = new ArrayList<>();

    private int controlRowY(int row) {
        return top + PANEL_Y + 5 + HEADER_HEIGHT + row * CONTROL_ROW;
    }

    private static SlotRule cycled(SlotRule rule) {
        SlotRule.Mode[] modes = SlotRule.Mode.values();
        return new SlotRule(modes[(rule.mode().ordinal() + 1) % modes.length], rule.slot());
    }

    private void addTitleBar() {
        addRenderableWidget(new FlatButton(font, left + WIDTH - MARGIN - DISCARD_WIDTH,
                top + TITLE_Y - 4, DISCARD_WIDTH, CONTROL_HEIGHT - 2, discardLabel(), () -> {
            if (!discardArmed) {
                discardArmed = true;
                rebuildControls();
                return;
            }
            PlatformClientNetwork.sendToServer(new RoutinePayloads.Discard(source, revision));
            onClose();
        }, true));
    }

    private void addBottomBar() {
        FlatButton delete = new FlatButton(font, left + WIDTH - MARGIN - 110, top + BAR_Y,
                110, BAR_HEIGHT, Component.translatable("gui.chronoclones.editor.delete"), () -> {
            flushName();
            int index = selectedRow().action();
            PlatformClientNetwork.sendToServer(
                    new RoutinePayloads.RemoveAction(source, index, revision));
            revision++;
            routine = routine.without(index);
            selected = Math.clamp(index, 0, Math.max(0, actions().size() - 1));
            selectedStep = -1;
            scroll = 0;
            rebuildControls();
        });
        delete.active = !actions().isEmpty() && !selectedRow().isStep();
        addRenderableWidget(delete);
    }

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

    private void apply(ActionSettings settings) {
        routine = routine.withSettings(selectedRow().action(), settings);
        nameDirty = false;
        PlatformClientNetwork.sendToServer(
                new RoutinePayloads.EditAction(source, selectedRow().action(), settings, revision));
        revision++;
    }

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

    //? if >=26 {
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        drawBackground(g, mouseX, mouseY, partialTick);
    }
    //?} else {
    /*@Override
    public void renderBackground(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY,
                                 float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        drawBackground(g, mouseX, mouseY, partialTick);
    }
    *///?}

    /** The version-neutral half of the background pass; the override above is the 26.x shell. */
    private void drawBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        AnchorPanels.panel(g, left - 2, top - 2, WIDTH + 4, HEIGHT + 4);
        g.fill(left, top, left + WIDTH, top + HEIGHT, AnchorPanels.WINDOW);

        g.text(font, title, left + MARGIN, top + TITLE_Y, AnchorPanels.TEXT);
        String count = Component.translatable(
                actions().size() == 1
                        ? "gui.chronoclones.editor.count.one"
                        : "gui.chronoclones.editor.count",
                actions().size()).getString();
        g.text(font, count, left + WIDTH - MARGIN - DISCARD_WIDTH - 6 - font.width(count),
                top + TITLE_Y, AnchorPanels.MUTED);

        timeline(g);
        listPanel(g, mouseX, mouseY);
        detailsPane(g);
        timelineTooltip(g, mouseX, mouseY);
    }

    private void timelineTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int index = markAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        TimedAction timed = actions().get(index);
        List<Component> lines = List.of(
                Component.literal(rowTitle(timed)),
                Component.literal(summaryOf(timed)),
                Component.translatable("gui.chronoclones.editor.at", seconds(timed.tick())));
        //? if >=26 {
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        //?} else {
        /*g.renderComponentTooltip(font, lines, mouseX, mouseY);
        *///?}
    }

    private static String seconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0f);
    }

    private static final int MARK_REACH = 5;

    private static final int MARK_SIZE = 10;

    private int markAt(int mouseX, int mouseY) {
        int trackY = top + TIMELINE_Y + TIMELINE_HEIGHT - 9;
        if (mouseY < trackY - MARK_REACH || mouseY > trackY + MARK_REACH + 1) {
            return -1;
        }

        int nearest = -1;
        int best = MARK_REACH + 1;
        for (int index = 0; index < actions().size(); index++) {
            int distance = Math.abs(mouseX - markX(index));
            if (distance < best) {
                best = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    private int markX(int index) {
        int x = left + MARGIN + 7;
        int width = WIDTH - MARGIN * 2 - 14;
        int length = Math.max(routine.lengthTicks(), 1);
        return x + (width - 1) * Math.clamp(actions().get(index).tick(), 0, length) / length;
    }

    private void timeline(GuiGraphicsExtractor g) {
        AnchorPanels.panel(g, left + MARGIN, top + TIMELINE_Y, WIDTH - MARGIN * 2, TIMELINE_HEIGHT);

        int x = left + MARGIN + 10;
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
            boolean current = i == selected;
            ChronoAction action = actions().get(i).action();
            int at = markX(i);

            if (current) {
                AnchorPanels.outline(g, at - MARK_SIZE / 2 - 1, trackY + 1 - MARK_SIZE / 2 - 1,
                        MARK_SIZE + 2, MARK_SIZE + 2, AnchorPanels.TEXT);
            }
            if (!ActionIcon.draw(g, action, at - MARK_SIZE / 2, trackY + 1 - MARK_SIZE / 2,
                    MARK_SIZE)) {
                diamond(g, at, trackY + 1, current ? 4 : 3,
                        current ? AnchorPanels.TEXT : kindColour(action));
            }
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
        drawActionCard(g, timed, x, y, width, current);
    }

    private static final int CARD_STRIP = 26;
    private static final int CARD_ICON = 16;

    private void drawActionCard(GuiGraphicsExtractor g, TimedAction timed, int x, int y, int width,
                                boolean current) {
        int colour = kindColour(timed.action());
        int top = y + 1;
        int bottom = y + ROW_HEIGHT - 2;

        g.fill(x + 3, top, x + width - 3, bottom, AnchorPanels.wash(colour, current ? 42 : 26));
        g.fill(x + 3, top, x + 3 + CARD_STRIP, bottom, AnchorPanels.wash(colour, 90));

        String at = seconds(timed.tick()) + "s";
        g.text(font, at, x + 3 + (CARD_STRIP - font.width(at)) / 2, y + 7, AnchorPanels.TEXT);

        int iconX = x + 3 + CARD_STRIP + 4;
        if (!ActionIcon.draw(g, timed.action(), iconX, y + (ROW_HEIGHT - CARD_ICON) / 2, CARD_ICON)) {
            diamond(g, iconX + CARD_ICON / 2, y + ROW_HEIGHT / 2, 3, colour);
        }

        int textX = iconX + CARD_ICON + 4;
        int textWidth = x + width - 5 - textX;
        g.text(font, font.plainSubstrByWidth(rowTitle(timed), textWidth), textX, y + 3,
                current ? AnchorPanels.TEXT : colour);
        g.text(font, font.plainSubstrByWidth(summaryOf(timed), textWidth), textX, y + 12,
                AnchorPanels.MUTED);
    }

    private void drawStepRow(GuiGraphicsExtractor g, Row row, TimedAction timed,
                             int x, int y, int width, boolean current) {
        SessionStep step = steps(row.action()).get(row.step());
        StepSettings rule = timed.settings().step(row.step());

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

        for (Labelled labelled : rows) {
            label(g, labelled.row(), labelled.labelKey());
        }
        if (hasName()) {
            nameTrack(g, nameRow());
        }
    }

    private void nameTrack(GuiGraphicsExtractor g, int row) {
        AnchorPanels.track(g, controlX(), controlRowY(row), CONTROL_WIDTH, CONTROL_HEIGHT);
    }

    private void label(GuiGraphicsExtractor g, int row, String key) {
        g.text(font, Component.translatable(key), left + PANE_X + 6,
                controlRowY(row) + (CONTROL_HEIGHT - font.lineHeight) / 2 + 1,
                AnchorPanels.MUTED);
    }

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

    //? if >=26 {
    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubled) {
        return handleClick(event.x(), event.y()) || super.mouseClicked(event, doubled);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double x, double y, int button) {
        return handleClick(x, y) || super.mouseClicked(x, y, button);
    }
    *///?}

    /** The version-neutral half of the click pass; the override above is the 26.x shell. */
    private boolean handleClick(double x, double y) {
        int mark = markAt((int) x, (int) y);
        if (mark >= 0) {
            select(new Row(mark, -1));
            return true;
        }

        Row row = rowAt((int) x, (int) y);
        if (row != null && !row.equals(selectedRow())) {
            select(row);
            return true;
        }
        return false;
    }

    private void select(Row row) {
        if (row.equals(selectedRow())) {
            return;
        }
        flushName();
        selected = row.action();
        selectedStep = row.step();
        discardArmed = false;
        rebuildControls();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return handleScroll(deltaY) || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    /** The version-neutral half of the scroll pass; the override above is the 26.x shell. */
    private boolean handleScroll(double deltaY) {
        int overflow = rows().size() - rowsVisible();
        if (overflow > 0) {
            scroll = Math.clamp(scroll - (int) Math.signum(deltaY), 0, overflow);
            return true;
        }
        return false;
    }

    private @org.jspecify.annotations.Nullable Row rowAt(int x, int y) {
        if (x < left + MARGIN || x >= left + MARGIN + LIST_WIDTH || y < top + ROWS_Y) {
            return null;
        }
        int line = (y - (top + ROWS_Y)) / ROW_HEIGHT;
        int index = scroll + line;
        List<Row> rows = rows();
        return line < rowsVisible() && index < rows.size() ? rows.get(index) : null;
    }

    //? if >=26 {
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (minecraft != null && minecraft.options.keyInventory.matches(event)
                && !isTyping()
                && source.anchor().isPresent()) {
            flushName();
            PlatformClientNetwork.sendToServer(
                    new RoutinePayloads.Reopen(source.anchor().get()));
            return true;
        }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)
                && !isTyping()
                && source.anchor().isPresent()) {
            flushName();
            PlatformClientNetwork.sendToServer(
                    new RoutinePayloads.Reopen(source.anchor().get()));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    *///?}

    private boolean isTyping() {
        return getFocused() instanceof EditBox box && box.canConsumeInput();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String rowTitle(TimedAction timed) {
        return RecordingDetail.title(timed).getString();
    }

    private String stepTitle(SessionStep step, StepSettings rule) {
        return rule.hasName()
                ? rule.name()
                : Component.translatable("gui.chronoclones.editor.step."
                        + step.kind().getSerializedName()).getString();
    }

    private String summaryOf(TimedAction timed) {
        return RecordingDetail.subtitle(timed.action()).getString();
    }

    private Component slotLabel(SlotRule rule) {
        if (rule.mode() == SlotRule.Mode.ANY) {
            return Component.translatable("gui.chronoclones.editor.slot.any");
        }
        if (rule.slot() < 0) {
            return Component.translatable(rule.mode() == SlotRule.Mode.EXACT
                    ? "gui.chronoclones.editor.slot.recorded_only"
                    : "gui.chronoclones.editor.slot.recorded_first");
        }
        return Component.translatable(rule.mode() == SlotRule.Mode.EXACT
                ? "gui.chronoclones.editor.slot.exact"
                : "gui.chronoclones.editor.slot.prefer", slotName(rule.slot()));
    }

    private Component toolLabel() {
        if (settings().tool() != ActionSettings.ToolRule.EXACT) {
            return Component.translatable(
                    "gui.chronoclones.editor.tool." + settings().tool().getSerializedName());
        }
        ItemStack recorded = switch (action()) {
            case ChronoAction.BreakBlock breaking -> breaking.toolTemplate();
            case ChronoAction.AttackEntity swinging -> swinging.weaponTemplate();
            default -> ItemStack.EMPTY;
        };
        return recorded.isEmpty()
                ? Component.translatable("gui.chronoclones.editor.tool.hands")
                : recorded.getHoverName();
    }

    private String subjectOption() {
        return switch (action()) {
            case ChronoAction.PlaceBlock ignored -> "material";
            case ChronoAction.AttackEntity ignored -> "target_type";
            case ChronoAction.InteractEntity ignored -> "target_type";
            case ChronoAction.UseContainer ignored -> "target_type";
            default -> "target_block";
        };
    }

    private Component subjectValue() {
        Component recorded = recordedSubjectName();
        if (settings().recordedSubject()) {
            return picksACreature()
                    ? Component.translatable("gui.chronoclones.editor.subject.only", recorded)
                    : recorded;
        }
        return picksACreature()
                ? Component.translatable("gui.chronoclones.editor.subject.prefer", recorded)
                : Component.translatable("gui.chronoclones.editor.subject.any_block");
    }

    private Component recordedSubjectName() {
        return switch (action()) {
            case ChronoAction.BreakBlock a -> a.expectedBlock().value().getName();
            case ChronoAction.PlaceBlock a -> Component.translatable(
                    a.item().value().getDescriptionId());
            case ChronoAction.UseOnBlock a when a.expectedBlock().isPresent() ->
                    a.expectedBlock().get().value().getName();
            case ChronoAction.AttackEntity a -> a.expectedType().value().getDescription();
            case ChronoAction.InteractEntity a -> a.expectedType().value().getDescription();
            case ChronoAction.UseContainer a when a.target() instanceof MenuTarget.Entity entity ->
                    entity.expectedType().value().getDescription();
            default -> Component.translatable("gui.chronoclones.editor.subject.any_block");
        };
    }

    private boolean picksACreature() {
        return action() instanceof ChronoAction.AttackEntity
                || action() instanceof ChronoAction.InteractEntity
                || action() instanceof ChronoAction.UseContainer;
    }

    private Component completionLabel() {
        return Component.translatable("gui.chronoclones.editor.completion."
                + settings().target().completion().getSerializedName());
    }

    private Component itemsLabel() {
        List<Holder<Item>> items = settings().transfer().items();
        return items.isEmpty()
                ? Component.translatable("gui.chronoclones.editor.items.any")
                : Component.translatable("gui.chronoclones.editor.items.only", items.size());
    }

    private Component stepItemsLabel(StepSettings step) {
        List<Holder<Item>> items = step.items();
        return items.isEmpty()
                ? Component.translatable("gui.chronoclones.editor.items.any")
                : Component.translatable(items.getFirst().value().getDescriptionId());
    }

    private Component amountLabel(StepSettings step, SessionStep.Move move) {
        return step.amount()
                .map(RoutineEditorScreen::amountName)
                .orElseGet(() -> Component.translatable("gui.chronoclones.editor.amount.recorded",
                        amountName(move.observed())));
    }

    private static Component amountName(SessionStep.Amount amount) {
        return Component.translatable(
                "gui.chronoclones.editor.amount." + amount.getSerializedName());
    }

    private static String checkboxKey(String option, boolean value) {
        return "gui.chronoclones.editor." + option + (value ? ".on" : ".off");
    }

    private Component discardLabel() {
        return Component.translatable(discardArmed
                ? "gui.chronoclones.editor.discard.confirm"
                : "gui.chronoclones.editor.discard");
    }
}
