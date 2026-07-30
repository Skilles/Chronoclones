package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a session's clicks as the moves the player meant by them.
 */
class SessionStepsTest {

    /** A holder, not a stack: an item's default components are not bound in the JUnit bootstrap. */
    private static final Holder<Item> STONE = BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE);

    private static SessionSteps.Event clicked(int slot, int button, ContainerInput input,
                                              Optional<Holder<Item>> slotItem,
                                              boolean heldBefore, boolean heldAfter) {
        return new SessionSteps.Event.Clicked(new SessionSteps.Observation(
                slot, button, input, slotItem, heldBefore, heldAfter));
    }

    /** A click that fills the cursor from a square holding stone. */
    private static SessionSteps.Event take(int slot, int button) {
        return clicked(slot, button, ContainerInput.PICKUP, Optional.of(STONE), false, true);
    }

    /** A click that empties the cursor into a square. */
    private static SessionSteps.Event put(int slot, int button) {
        return clicked(slot, button, ContainerInput.PICKUP, Optional.empty(), true, false);
    }

    /** A click that puts one down and keeps the rest. */
    private static SessionSteps.Event putOne(int slot) {
        return clicked(slot, 1, ContainerInput.PICKUP, Optional.empty(), true, true);
    }

    @Test
    @DisplayName("a take and a put are one move")
    void pairBecomesOneMove() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(take(4, 0), put(54, 0)));

        assertEquals(1, steps.size(), "steps: " + steps);
        SessionStep.Move move = assertInstanceOf(SessionStep.Move.class, steps.getFirst());
        assertEquals(4, move.from());
        assertEquals(54, move.to());
        assertEquals(STONE.value(), move.item().value());
        assertEquals(SessionStep.Amount.ALL, move.observed());
    }

    @Test
    @DisplayName("a right-click take is half of whatever is there, not a count")
    void rightClickTakeIsHalf() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(take(4, 1), put(54, 0)));

        SessionStep.Move move = assertInstanceOf(SessionStep.Move.class, steps.getFirst());
        assertEquals(SessionStep.Amount.HALF, move.observed());
    }

    @Test
    @DisplayName("dropping singles is one move each, and the cursor stays picked up")
    void rightClickPlacesReadAsSingles() {
        List<SessionStep> steps = SessionSteps.interpret(
                List.of(take(4, 0), putOne(54), putOne(55), put(56, 0)));

        assertEquals(3, steps.size(), "steps: " + steps);
        assertEquals(SessionStep.Amount.ONE,
                assertInstanceOf(SessionStep.Move.class, steps.get(0)).observed());
        assertEquals(55, assertInstanceOf(SessionStep.Move.class, steps.get(1)).to());
        // The last click emptied the cursor, so it moved everything that was left.
        assertEquals(SessionStep.Amount.ALL,
                assertInstanceOf(SessionStep.Move.class, steps.get(2)).observed());
    }

    @Test
    @DisplayName("a shift-click is a move whose destination the menu chooses")
    void quickMoveKeepsItsDestinationOpen() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                clicked(4, 0, ContainerInput.QUICK_MOVE,
                        Optional.of(STONE), false, false)));

        SessionStep.Move move = assertInstanceOf(SessionStep.Move.class, steps.getFirst());
        assertTrue(move.quick(), "a shift-click named a destination it never had");
        assertEquals(SessionStep.Move.ELSEWHERE, move.to());
        assertEquals(4, move.from());
    }

    /** Vanilla's encoding: the low two bits are the stage, the next two the kind of drag. */
    private static SessionSteps.Event drag(int slot, int type, int stage, boolean heldAfter) {
        return clicked(slot, stage | type << 2, ContainerInput.QUICK_CRAFT,
                Optional.empty(), true, heldAfter);
    }

    @Test
    @DisplayName("a drag across several squares stays raw, because no one click distributes a stack")
    void wideDragIsNotInterpreted() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                drag(-999, 1, 0, true),
                drag(5, 1, 1, true),
                drag(6, 1, 1, true),
                drag(-999, 1, 2, true)));

        assertEquals(4, steps.size(), "steps: " + steps);
        for (SessionStep step : steps) {
            assertInstanceOf(SessionStep.RawClick.class, step);
        }
    }

    @Test
    @DisplayName("dropping one item into a square is a move, however the mouse got there")
    void oneSquareDragIsTheClickItAmountsTo() {
        // Holding right and letting go over one square arrives as a three-part drag rather than a
        // click if the mouse twitched. Vanilla turns that back into one right-click, and so does
        // this: otherwise "take half, drop one, put the rest back" is five rows nobody can edit.
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                take(4, 1),
                drag(-999, 1, 0, true),
                drag(54, 1, 1, true),
                drag(-999, 1, 2, true),
                put(4, 0)));

        // One row, not five: the last click puts the rest back where it came from, which moves
        // nothing that replaying the move does not already put back.
        assertEquals(1, steps.size(), "steps: " + steps);
        SessionStep.Move move = assertInstanceOf(SessionStep.Move.class, steps.getFirst());
        assertEquals(4, move.from());
        assertEquals(54, move.to());
        assertEquals(SessionStep.Amount.ONE, move.observed());
    }

    @Test
    @DisplayName("picking a stack up and putting it straight back is not a step at all")
    void puttingItStraightBackIsNothing() {
        assertEquals(List.of(), SessionSteps.interpret(List.of(take(4, 1), put(4, 0))));
    }

    @Test
    @DisplayName("a left-drag onto one square puts down everything held, as its click would")
    void oneSquareLeftDragPlacesAll() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                take(4, 0),
                drag(-999, 0, 0, true),
                drag(54, 0, 1, false),
                drag(-999, 0, 2, false)));

        SessionStep.Move move = assertInstanceOf(SessionStep.Move.class, steps.getFirst());
        assertEquals(SessionStep.Amount.ALL, move.observed());
    }

    @Test
    @DisplayName("a drag with no end is left exactly as it arrived")
    void unfinishedDragIsNotCollapsed() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                drag(-999, 1, 0, true),
                drag(54, 1, 1, true)));

        assertEquals(2, steps.size(), "steps: " + steps);
        assertInstanceOf(SessionStep.RawClick.class, steps.get(0));
    }

    @Test
    @DisplayName("a swap loses both halves of the move rather than naming one wrongly")
    void swapFallsBackToBothClicks() {
        // Picking up stone and left-clicking a square holding something else swaps them: the cursor
        // comes away full, which is not a move, and the take is no longer half of anything.
        List<SessionSteps.Event> clicks = List.of(take(4, 0),
                clicked(54, 0, ContainerInput.PICKUP,
                        Optional.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.DIRT)), true, true));

        List<SessionStep> steps = SessionSteps.interpret(clicks);

        assertEquals(List.of(new SessionStep.RawClick(4, 0, ContainerInput.PICKUP),
                new SessionStep.RawClick(54, 0, ContainerInput.PICKUP)), steps);
    }

    @Test
    @DisplayName("a take with nowhere to land is still recorded")
    void danglingTakeSurvives() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(take(4, 0)));

        assertEquals(List.of(new SessionStep.RawClick(4, 0, ContainerInput.PICKUP)), steps);
    }

    @Test
    @DisplayName("a session of nothing interprets to nothing")
    void emptySessionIsEmpty() {
        assertEquals(List.of(), SessionSteps.interpret(List.of()));
    }

    @Test
    @DisplayName("a throw outside the window is a raw click, not a move to nowhere")
    void throwOutsideIsRaw() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(take(4, 0),
                clicked(-999, 0, ContainerInput.THROW,
                        Optional.empty(), true, false)));

        assertEquals(2, steps.size(), "steps: " + steps);
        assertInstanceOf(SessionStep.RawClick.class, steps.get(0));
        assertEquals(-999, assertInstanceOf(SessionStep.RawClick.class, steps.get(1)).slot());
    }

    @Test
    @DisplayName("clicking an empty square is not the start of a move")
    void clickOnNothingIsRaw() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                clicked(4, 0, ContainerInput.PICKUP,
                        Optional.empty(), false, false)));

        assertEquals(List.of(new SessionStep.RawClick(4, 0, ContainerInput.PICKUP)), steps);
    }

    @Test
    @DisplayName("a name typed letter by letter is one step, for the name that was settled on")
    void renamesCollapse() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                did(new SessionStep.Rename("T")),
                did(new SessionStep.Rename("Tu")),
                did(new SessionStep.Rename("Tunneler"))));

        assertEquals(List.of(new SessionStep.Rename("Tunneler")), steps);
    }

    @Test
    @DisplayName("two names with work between them are two steps")
    void separatedRenamesBothSurvive() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                did(new SessionStep.Rename("First")),
                take(4, 0), put(0, 0),
                did(new SessionStep.Rename("Second"))));

        assertEquals(3, steps.size(), "steps: " + steps);
        assertEquals(new SessionStep.Rename("First"), steps.get(0));
        assertEquals(new SessionStep.Rename("Second"), steps.get(2));
    }

    @Test
    @DisplayName("a step that is not a click gives up on a pickup left in the air")
    void aKnownStepFlushesADanglingPickup() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                take(4, 0), did(new SessionStep.Button(1)), put(54, 0)));

        // The take could not have been part of a move across the button, so all three stand alone.
        assertEquals(List.of(new SessionStep.RawClick(4, 0, ContainerInput.PICKUP),
                new SessionStep.Button(1),
                new SessionStep.RawClick(54, 0, ContainerInput.PICKUP)), steps);
    }

    private static SessionSteps.Event did(SessionStep step) {
        return new SessionSteps.Event.Did(step);
    }

    @Test
    @DisplayName("moves and raw clicks keep the order the player made them in")
    void orderIsPreserved() {
        List<SessionStep> steps = SessionSteps.interpret(List.of(
                take(4, 0), put(54, 0),
                // A swap leaves the cursor alone, so it interrupts nothing and names nothing.
                clicked(9, 3, ContainerInput.SWAP,
                        Optional.of(STONE), false, false),
                take(5, 0), put(55, 0)));

        assertEquals(List.of(SessionStep.Kind.MOVE, SessionStep.Kind.RAW_CLICK, SessionStep.Kind.MOVE),
                steps.stream().map(SessionStep::kind).toList());
    }
}
