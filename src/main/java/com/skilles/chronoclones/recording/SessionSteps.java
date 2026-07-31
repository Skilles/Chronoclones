package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** Turns the raw clicks of a container session into the steps that describe them. */
public final class SessionSteps {

    private SessionSteps() {}

    public record Observation(int slot, int button, ContainerInput input,
                              Optional<Holder<Item>> slotItem,
                              boolean heldBefore, boolean heldAfter) {
        public SessionStep.RawClick raw() {
            return new SessionStep.RawClick(slot, button, input);
        }
    }

    public sealed interface Event {

        record Clicked(Observation observation) implements Event {}

        record Did(SessionStep step) implements Event {}
    }

    public static List<SessionStep> interpret(List<Event> events) {
        List<SessionStep> steps = new ArrayList<>();

        Observation pickup = null;

        for (Event event : collapseDrags(events)) {
            if (event instanceof Event.Did done) {
                if (pickup != null) {
                    steps.add(pickup.raw());
                    pickup = null;
                }
                if (supersedes(done.step(), steps.isEmpty() ? null : steps.getLast())) {
                    steps.set(steps.size() - 1, done.step());
                    continue;
                }
                steps.add(done.step());
                continue;
            }

            Observation click = ((Event.Clicked) event).observation();
            if (pickup == null) {
                if (isQuickMove(click)) {
                    steps.add(new SessionStep.Move(click.slot(), SessionStep.Move.ELSEWHERE,
                            click.slotItem().orElseThrow(), SessionStep.Amount.ALL));
                } else if (isPickup(click)) {
                    pickup = click;
                } else {
                    steps.add(click.raw());
                }
                continue;
            }

            SessionStep.Amount placed = placement(pickup, click);
            if (placed == null) {
                steps.add(pickup.raw());
                steps.add(click.raw());
                pickup = null;
                continue;
            }

            if (pickup.slot() != click.slot()) {
                steps.add(new SessionStep.Move(pickup.slot(), click.slot(),
                        pickup.slotItem().orElseThrow(), placed));
            }
            if (!click.heldAfter()) {
                pickup = null;
            }
        }

        if (pickup != null) {
            steps.add(pickup.raw());
        }
        return steps;
    }

    private static boolean supersedes(SessionStep step, @Nullable SessionStep previous) {
        if (previous == null) {
            return false;
        }
        return switch (step) {
            case SessionStep.Rename ignored -> previous instanceof SessionStep.Rename;
            case SessionStep.Trade trade -> previous instanceof SessionStep.Trade before
                    && before.sameOffer(trade);
            default -> false;
        };
    }

    private static List<Event> collapseDrags(List<Event> events) {
        List<Event> collapsed = new ArrayList<>(events.size());

        for (int index = 0; index < events.size(); index++) {
            boolean oneSquare = isDragStage(events.get(index), DRAG_START)
                    && index + 2 < events.size()
                    && isDragStage(events.get(index + 1), DRAG_ADD)
                    && isDragStage(events.get(index + 2), DRAG_END);
            if (!oneSquare) {
                collapsed.add(events.get(index));
                continue;
            }

            Observation start = clickOf(events.get(index));
            Observation onto = clickOf(events.get(index + 1));
            Observation finish = clickOf(events.get(index + 2));
            collapsed.add(new Event.Clicked(new Observation(onto.slot(), type(start.button()),
                    ContainerInput.PICKUP, onto.slotItem(), start.heldBefore(), finish.heldAfter())));
            index += 2;
        }
        return collapsed;
    }

    private static final int DRAG_START = 0;
    private static final int DRAG_ADD = 1;
    private static final int DRAG_END = 2;

    private static int header(int button) {
        return button & 3;
    }

    private static int type(int button) {
        return button >> 2 & 3;
    }

    private static boolean isDragStage(Event event, int stage) {
        Observation click = clickOf(event);
        return click != null && click.input() == ContainerInput.QUICK_CRAFT
                && header(click.button()) == stage;
    }

    private static @Nullable Observation clickOf(Event event) {
        return event instanceof Event.Clicked clicked ? clicked.observation() : null;
    }

    private static boolean isQuickMove(Observation click) {
        return click.input() == ContainerInput.QUICK_MOVE && click.slotItem().isPresent();
    }

    private static boolean isPickup(Observation click) {
        return click.input() == ContainerInput.PICKUP
                && click.slot() >= 0
                && !click.heldBefore()
                && click.heldAfter()
                && click.slotItem().isPresent();
    }

    private static SessionStep.@Nullable Amount placement(Observation pickup, Observation click) {
        if (click.input() != ContainerInput.PICKUP || click.slot() < 0 || !click.heldBefore()) {
            return null;
        }
        if (click.heldAfter()) {
            return click.button() == 1 ? SessionStep.Amount.ONE : null;
        }
        return pickup.button() == 1 ? SessionStep.Amount.HALF : SessionStep.Amount.ALL;
    }
}
