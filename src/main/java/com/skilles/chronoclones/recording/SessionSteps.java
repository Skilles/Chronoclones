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

    public static List<SessionStep> interpret(List<Observation> observations) {
        List<SessionStep> steps = new ArrayList<>();

        // The click that filled the cursor, still waiting to learn where its item went.
        Observation pickup = null;

        for (Observation click : observations) {
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

            steps.add(new SessionStep.Move(pickup.slot(), click.slot(),
                    pickup.slotItem().orElseThrow(), placed));
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
