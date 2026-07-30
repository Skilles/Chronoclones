package com.skilles.chronoclones.recording;

import java.util.List;

import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.StepSettings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-step override layer, whose whole job is to change nothing until it is asked to.
 */
class StepSettingsTest {

    @Test
    @DisplayName("an unedited step runs, is unnamed, and narrows nothing")
    void defaultsChangeNothing() {
        StepSettings step = StepSettings.DEFAULT;

        assertTrue(step.enabled());
        assertTrue(!step.hasName());
        assertEquals(SlotRule.Mode.PREFER, step.slot().mode());
        assertTrue(step.transfer().items().isEmpty(), "an unedited step carries anything");
        assertEquals(Integer.MAX_VALUE, step.transfer().quantity().budget());
    }

    @Test
    @DisplayName("a step nobody has edited costs nothing to ask about")
    void unlistedStepsReadAsDefault() {
        ActionSettings settings = ActionSettings.DEFAULT;

        assertTrue(settings.steps().isEmpty(), "an unedited action lists no steps at all");
        assertSame(StepSettings.DEFAULT, settings.step(0));
        assertSame(StepSettings.DEFAULT, settings.step(7));
        // A negative index is the action itself, which is not a step and has no settings of one.
        assertSame(StepSettings.DEFAULT, settings.step(-1));
    }

    @Test
    @DisplayName("editing a later step leaves the ones before it alone")
    void reachingAStepPadsWithDefaults() {
        ActionSettings settings = ActionSettings.DEFAULT.withStep(
                3, StepSettings.DEFAULT.withEnabled(false));

        assertEquals(4, settings.steps().size());
        for (int index = 0; index < 3; index++) {
            assertTrue(settings.step(index).enabled(),
                    "step " + index + " was changed by an edit to step 3");
        }
        assertTrue(!settings.step(3).enabled());
    }

    @Test
    @DisplayName("a step edit leaves the action's own settings alone")
    void stepEditsDoNotTouchTheAction() {
        ActionSettings named = ActionSettings.DEFAULT.withName("Restock")
                .withSlot(SlotRule.prefer(4));

        ActionSettings edited = named.withStep(0, StepSettings.DEFAULT.withEnabled(false));

        assertEquals("Restock", edited.name());
        assertEquals(named.slot(), edited.slot());
        assertEquals(named.target(), edited.target());
    }

    @Test
    @DisplayName("a negative index cannot grow the list")
    void negativeIndexIsRefused() {
        assertEquals(List.of(),
                ActionSettings.DEFAULT.withStep(-1, StepSettings.DEFAULT.withEnabled(false)).steps());
    }
}
