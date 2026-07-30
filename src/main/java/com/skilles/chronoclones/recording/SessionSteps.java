package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Turns the clicks of one container session into what the player meant by them.
 *
 * <p>Interpretation happens once, when the session closes, rather than during capture: a pickup only
 * becomes a move when its other half arrives. {@link AttackIntent} is the same shape.
 *
 * <p>Anything it cannot name stays a {@link SessionStep.RawClick}, which replays exactly as it did
 * before any of this existed. Being unable to interpret a click is never a reason to lose it.
 */
public final class SessionSteps {

    private SessionSteps() {}

    /**
     * One click, and the little of the menu's state that naming it requires.
     *
     * <p>The cursor is a boolean at each end because that is all the interpreter asks of it: "the
     * cursor gained 32" is equally a right-click on a stack of 64 and a left-click on a stack of 32,
     * and the difference is the button, not the count.
     *
     * @param slotItem   what was in the clicked square before the click, if anything
     * @param heldBefore whether the cursor was holding something as the click arrived
     * @param heldAfter  whether it still is
     */
    public record Observation(int slot, int button, ContainerInput input,
                              Optional<Holder<Item>> slotItem,
                              boolean heldBefore, boolean heldAfter) {

        public SessionStep.RawClick raw() {
            return new SessionStep.RawClick(slot, button, input);
        }
    }

    /**
     * Something that happened in the menu, in the order it happened.
     */
    public sealed interface Event {

        /** A click, which only its neighbours can explain. */
        record Clicked(Observation observation) implements Event {}

        /** A trade, a button, a rename: these arrive already named, each by its own packet. */
        record Did(SessionStep step) implements Event {}
    }

    public static List<SessionStep> interpret(List<Event> events) {
        List<SessionStep> steps = new ArrayList<>();

        // The click that filled the cursor, still waiting to learn where its item went.
        Observation pickup = null;

        for (Event event : collapseDrags(events)) {
            if (event instanceof Event.Did done) {
                // Something the clicks around it cannot be part of, so the cursor's business ends.
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
                // Not the other half of the move: give up on naming either end of it.
                steps.add(pickup.raw());
                steps.add(click.raw());
                pickup = null;
                continue;
            }

            // Putting the rest of a stack back where it came from moves nothing, and replaying a
            // move already returns what its destination would not take.
            if (pickup.slot() != click.slot()) {
                steps.add(new SessionStep.Move(pickup.slot(), click.slot(),
                        pickup.slotItem().orElseThrow(), placed));
            }
            // A right-click puts one item down and keeps the rest, so the pickup is still open.
            if (!click.heldAfter()) {
                pickup = null;
            }
        }

        // The cursor never landed anywhere nameable, so the click that filled it stands alone.
        if (pickup != null) {
            steps.add(pickup.raw());
        }
        return steps;
    }

    /**
     * Whether a step simply replaces the one before it rather than following it.
     *
     * <p>An anvil's name field sends a packet per keystroke, and only the name the player stopped
     * typing is the name they meant. Choosing a merchant's offer is the same shape: selecting it
     * again refills the payment squares and buys nothing, so two in a row are one choice, where two
     * separated by taking the result are two purchases.
     */
    private static boolean supersedes(SessionStep step, @Nullable SessionStep previous) {
        if (previous == null) {
            return false;
        }
        return switch (step) {
            case SessionStep.Rename ignored -> previous instanceof SessionStep.Rename;
            case SessionStep.Trade trade -> previous.equals(trade);
            default -> false;
        };
    }

    /**
     * Rewrites a drag across a single square as the click it amounts to.
     *
     * <p>Dropping one item into a square means holding right and letting go, and the mouse moving a
     * pixel in between turns that into a three-part quick-craft drag rather than a click. Vanilla
     * itself collapses a one-square drag back into {@code doClick(slot, type, PICKUP)}; doing the same
     * here is what lets "take half, drop one, put the rest back" read as one move rather than five
     * clicks nobody can configure.
     *
     * <p>A drag across several squares is left alone: it distributes a stack, and no single click
     * does that.
     */
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

    /** Vanilla's own encoding: the low two bits are the stage, the next two the kind of drag. */
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

    /** A shift-click is a move whose destination the menu picks, then and now. */
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

    /**
     * How much of the pickup this click put down, or null if it did something else entirely.
     */
    private static SessionStep.@Nullable Amount placement(Observation pickup, Observation click) {
        if (click.input() != ContainerInput.PICKUP || click.slot() < 0 || !click.heldBefore()) {
            return null;
        }
        if (click.heldAfter()) {
            // One item down and the rest still held. Anything else leaving something on the cursor
            // is a swap or a merge into a nearly full stack, neither of which is a plain move.
            return click.button() == 1 ? SessionStep.Amount.ONE : null;
        }
        return pickup.button() == 1 ? SessionStep.Amount.HALF : SessionStep.Amount.ALL;
    }
}
