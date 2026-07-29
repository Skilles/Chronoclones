package com.skilles.chronoclones.menu.client;

import java.util.List;

import com.skilles.chronoclones.item.RecordingDetail;
import com.skilles.chronoclones.network.RoutinePayloads;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.QuantityRule;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ActionSettings.TransferRule;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

    private static final int WIDTH = 300;
    private static final int HEIGHT = 210;

    private static final int MARGIN = 8;
    private static final int TITLE_Y = 8;
    private static final int TIMELINE_Y = 22;
    private static final int TIMELINE_HEIGHT = 7;
    private static final int LIST_Y = 38;
    private static final int LIST_WIDTH = 150;
    private static final int ROW_HEIGHT = 14;
    private static final int PANE_GAP = 6;

    private static final int CONTROL_HEIGHT = 16;
    private static final int CONTROL_GAP = 4;

    private final RoutinePayloads.Source source;

    private Recording routine;
    private int selected;
    private int scroll;

    private int left;
    private int top;

    private EditBox nameBox;

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
        return (HEIGHT - LIST_Y - MARGIN) / ROW_HEIGHT;
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

        int paneX = left + MARGIN + LIST_WIDTH + PANE_GAP;
        int paneWidth = WIDTH - MARGIN * 2 - LIST_WIDTH - PANE_GAP;
        addDiscardButton(paneX, paneWidth);

        if (actions().isEmpty()) {
            return;
        }

        int y = top + LIST_Y;
        ActionSettings settings = settings();

        nameBox = new EditBox(font, paneX, y, paneWidth, CONTROL_HEIGHT,
                Component.translatable("gui.chronoclones.editor.name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(settings.name());
        nameBox.setHint(RecordingDetail.summary(action()));
        nameBox.setResponder(text -> apply(settings().withName(text)));
        addRenderableWidget(nameBox);
        y += CONTROL_HEIGHT + CONTROL_GAP;

        y = addSlotControl(paneX, paneWidth, y);
        if (isTargeted()) {
            addTargetControls(paneX, paneWidth, y);
        } else if (action() instanceof ChronoAction.UseContainer session) {
            addTransferControls(paneX, paneWidth, y, session);
        }
    }

    private void addTransferControls(int x, int width, int y, ChronoAction.UseContainer session) {
        addRenderableWidget(Button.builder(itemsLabel(), button -> {
            TransferRule rule = settings().transfer();
            apply(settings().withTransfer(rule.withItems(rule.items().isEmpty()
                    ? carriedItems(session)
                    : List.of())));
            button.setMessage(itemsLabel());
        }).bounds(x, y, width, CONTROL_HEIGHT).build());
        y += CONTROL_HEIGHT + CONTROL_GAP;

        addRenderableWidget(new QuantitySlider(x, y, width, CONTROL_HEIGHT,
                settings().transfer().quantity(),
                cap -> apply(settings().withTransfer(
                        settings().transfer().withQuantity(QuantityRule.atMost(cap))))));
    }

    /** The items the session was recorded carrying, which is what "only these" means. */
    private static List<Holder<Item>> carriedItems(ChronoAction.UseContainer session) {
        return session.carrier().stream()
                .map(carried -> carried.stack().getItem())
                .distinct()
                .map(BuiltInRegistries.ITEM::wrapAsHolder)
                .toList();
    }

    /** Bottom of the pane, away from everything that only changes a reading. */
    private void addDiscardButton(int x, int width) {
        addRenderableWidget(Button.builder(discardLabel(), button -> {
            if (!discardArmed) {
                discardArmed = true;
                button.setMessage(discardLabel());
                return;
            }
            ClientPacketDistributor.sendToServer(new RoutinePayloads.Discard(source));
            onClose();
        }).bounds(x, top + HEIGHT - MARGIN - CONTROL_HEIGHT, width, CONTROL_HEIGHT).build());
    }

    private int addSlotControl(int x, int width, int y) {
        addRenderableWidget(Button.builder(slotLabel(), button -> {
            SlotRule rule = settings().slot();
            SlotRule.Mode[] modes = SlotRule.Mode.values();
            apply(settings().withSlot(new SlotRule(
                    modes[(rule.mode().ordinal() + 1) % modes.length], rule.slot())));
            button.setMessage(slotLabel());
        }).bounds(x, y, width, CONTROL_HEIGHT).build());

        return y + CONTROL_HEIGHT + CONTROL_GAP;
    }

    private void addTargetControls(int x, int width, int y) {
        addRenderableWidget(Button.builder(completionLabel(), button -> {
            TargetRule rule = settings().target();
            apply(settings().withTarget(rule.withCompletion(
                    rule.completion() == TargetRule.Completion.ONCE
                            ? TargetRule.Completion.UNTIL_DEAD
                            : TargetRule.Completion.ONCE)));
            button.setMessage(completionLabel());
        }).bounds(x, y, width, CONTROL_HEIGHT).build());
        y += CONTROL_HEIGHT + CONTROL_GAP;

        addRenderableWidget(Button.builder(stickyLabel(), button -> {
            TargetRule rule = settings().target();
            apply(settings().withTarget(rule.withSticky(!rule.sticky())));
            button.setMessage(stickyLabel());
        }).bounds(x, y, width, CONTROL_HEIGHT).build());
        y += CONTROL_HEIGHT + CONTROL_GAP;

        addRenderableWidget(Button.builder(filterLabel(), button -> {
            TargetRule rule = settings().target();
            apply(settings().withTarget(rule.withFilter(rule.filter().isEmpty()
                    ? List.of(recordedType())
                    : List.of())));
            button.setMessage(filterLabel());
        }).bounds(x, y, width, CONTROL_HEIGHT).build());
        y += CONTROL_HEIGHT + CONTROL_GAP;

        addRenderableWidget(new RadiusSlider(x, y, width, CONTROL_HEIGHT, settings().target(),
                radius -> apply(settings().withTarget(settings().target().withRadius(radius)))));
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

    private net.minecraft.core.Holder<net.minecraft.world.entity.EntityType<?>> recordedType() {
        return switch (action()) {
            case ChronoAction.AttackEntity a -> a.expectedType();
            case ChronoAction.InteractEntity a -> a.expectedType();
            default -> throw new IllegalStateException("not a targeted action");
        };
    }

    /**
     * Applies a change locally and tells the server, which re-checks it rather than trusting us.
     */
    private void apply(ActionSettings settings) {
        routine = routine.withSettings(selected, settings);
        ClientPacketDistributor.sendToServer(
                new RoutinePayloads.EditAction(source, selected, settings));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                  float partialTick) {
        super.extractBackground(extractor, mouseX, mouseY, partialTick);

        AnchorPanels.panel(extractor, left - 2, top - 2, WIDTH + 4, HEIGHT + 4);
        extractor.fill(left, top, left + WIDTH, top + HEIGHT, AnchorPanels.WINDOW);

        timeline(extractor);
        AnchorPanels.panel(extractor, left + MARGIN, top + LIST_Y - 3,
                LIST_WIDTH, HEIGHT - LIST_Y - MARGIN + 3);
        rows(extractor, mouseX, mouseY);
    }

    /** Where every action falls, with the selected one picked out. */
    private void timeline(GuiGraphicsExtractor extractor) {
        int x = left + MARGIN;
        int width = WIDTH - MARGIN * 2;
        AnchorPanels.track(extractor, x, top + TIMELINE_Y, width, TIMELINE_HEIGHT);

        int length = Math.max(routine.lengthTicks(), 1);
        for (int i = 0; i < actions().size(); i++) {
            int at = x + (width - 1) * Math.clamp(actions().get(i).tick(), 0, length) / length;
            extractor.fill(at, top + TIMELINE_Y, at + 1, top + TIMELINE_Y + TIMELINE_HEIGHT,
                    i == selected ? AnchorPanels.TEXT : AnchorPanels.ACCENT);
        }
    }

    private void rows(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        for (int row = 0; row < rowsVisible(); row++) {
            int index = scroll + row;
            if (index >= actions().size()) {
                return;
            }

            int y = top + LIST_Y + row * ROW_HEIGHT;
            boolean hovered = mouseX >= left + MARGIN && mouseX < left + MARGIN + LIST_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;

            if (index == selected) {
                AnchorPanels.outline(extractor, left + MARGIN + 2, y, LIST_WIDTH - 4, ROW_HEIGHT,
                        AnchorPanels.ACCENT);
            } else if (hovered) {
                extractor.fill(left + MARGIN + 3, y + 1, left + MARGIN + LIST_WIDTH - 3,
                        y + ROW_HEIGHT - 1, AnchorPanels.SLOT_EDGE);
            }

            TimedAction timed = actions().get(index);
            extractor.text(font, timed.tick() / 20 + "s", left + MARGIN + 6, y + 3,
                    AnchorPanels.MUTED);
            extractor.text(font, rowLabel(timed), left + MARGIN + 28, y + 3,
                    index == selected ? AnchorPanels.ACCENT : AnchorPanels.TEXT);
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        extractor.text(font, title, left + MARGIN, top + TITLE_Y, AnchorPanels.TEXT);

        String count = Component.translatable("gui.chronoclones.editor.count",
                actions().size()).getString();
        extractor.text(font, count, left + WIDTH - MARGIN - font.width(count), top + TITLE_Y,
                AnchorPanels.MUTED);

        if (actions().isEmpty()) {
            extractor.text(font, Component.translatable("gui.chronoclones.editor.empty"),
                    left + MARGIN + 6, top + LIST_Y + 4, AnchorPanels.MUTED);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubled) {
        int row = rowAt((int) event.x(), (int) event.y());
        if (row >= 0 && row != selected) {
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
        if (x < left + MARGIN || x >= left + MARGIN + LIST_WIDTH || y < top + LIST_Y) {
            return -1;
        }
        int row = (y - (top + LIST_Y)) / ROW_HEIGHT;
        int index = scroll + row;
        return row < rowsVisible() && index < actions().size() ? index : -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ labels

    private Component rowLabel(TimedAction timed) {
        return timed.settings().hasName()
                ? Component.literal(timed.settings().name())
                : RecordingDetail.summary(timed.action());
    }

    private Component slotLabel() {
        SlotRule rule = settings().slot();
        return Component.translatable("gui.chronoclones.editor.slot",
                Component.translatable("gui.chronoclones.editor.slot." + rule.mode().getSerializedName()),
                rule.slot() < 0 ? "-" : String.valueOf(rule.slot()));
    }

    private Component completionLabel() {
        return Component.translatable("gui.chronoclones.editor.completion",
                Component.translatable("gui.chronoclones.editor.completion."
                        + settings().target().completion().getSerializedName()));
    }

    private Component stickyLabel() {
        return Component.translatable(settings().target().sticky()
                ? "gui.chronoclones.editor.sticky.on"
                : "gui.chronoclones.editor.sticky.off");
    }

    private Component discardLabel() {
        return Component.translatable(discardArmed
                        ? "gui.chronoclones.editor.discard.confirm"
                        : "gui.chronoclones.editor.discard")
                .withStyle(discardArmed ? ChatFormatting.RED : ChatFormatting.GRAY);
    }

    private Component itemsLabel() {
        List<Holder<Item>> items = settings().transfer().items();
        return items.isEmpty()
                ? Component.translatable("gui.chronoclones.editor.items.any")
                : Component.translatable("gui.chronoclones.editor.items.only", items.size());
    }

    private Component filterLabel() {
        return settings().target().filter().isEmpty()
                ? Component.translatable("gui.chronoclones.editor.filter.any")
                : Component.translatable("gui.chronoclones.editor.filter.only",
                        recordedType().value().getDescription());
    }
}
