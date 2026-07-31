package com.skilles.chronoclones.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiagnosticStateTest {

    @Test
    @DisplayName("a no-charge halt clears as soon as there is charge")
    void noChargeRecoversWithCharge() {
        assertFalse(DiagnosticState.canResume(FailureReason.NO_CHARGE, false, true),
                "still empty, must stay halted");
        assertTrue(DiagnosticState.canResume(FailureReason.NO_CHARGE, true, true),
                "fuel was added, must resume without a re-imprint");
    }

    @Test
    @DisplayName("a full-inventory halt clears as soon as there is room")
    void inventoryFullRecoversWithRoom() {
        assertFalse(DiagnosticState.canResume(FailureReason.INVENTORY_FULL, true, false));
        assertTrue(DiagnosticState.canResume(FailureReason.INVENTORY_FULL, true, true));
    }

    @Test
    @DisplayName("a no-charge halt is not cleared by inventory space, or vice versa")
    void recoveryConditionsAreNotInterchangeable() {
        assertFalse(DiagnosticState.canResume(FailureReason.NO_CHARGE, false, true));
        assertFalse(DiagnosticState.canResume(FailureReason.INVENTORY_FULL, true, false));
    }

    @Test
    @DisplayName("every halting reason has a way to clear itself")
    void everyHaltIsRecoverable() {
        for (FailureReason reason : FailureReason.values()) {
            if (!reason.halts()) {
                continue;
            }
            assertTrue(DiagnosticState.canResume(reason, true, true),
                    reason + " halts but cannot be resumed even once everything is available: "
                            + "that is a permanent trap");
        }
    }

    @Test
    @DisplayName("non-halting reasons never block progress")
    void nonHaltingReasonsAlwaysResume() {
        for (FailureReason reason : FailureReason.values()) {
            if (reason.halts()) {
                continue;
            }
            assertTrue(DiagnosticState.canResume(reason, false, false),
                    reason + " does not halt, so it must never gate resumption");
        }
    }

    @Test
    @DisplayName("only resource-shaped failures halt; the rest skip one action")
    void onlyResourceFailuresHalt() {
        assertTrue(FailureReason.NO_CHARGE.halts());
        assertTrue(FailureReason.INVENTORY_FULL.halts());

        assertFalse(FailureReason.NO_BLOCK.halts());
        assertFalse(FailureReason.WRONG_BLOCK.halts());
        assertFalse(FailureReason.PROTECTED.halts());
        assertFalse(FailureReason.NO_ITEM.halts());
        assertFalse(FailureReason.NO_TARGET.halts());
        assertFalse(FailureReason.OBSTRUCTED.halts());
    }

    @Test
    @DisplayName("NONE is not a failure and never halts")
    void noneIsNotAFailure() {
        assertFalse(DiagnosticState.NONE.isFailure());
        assertFalse(DiagnosticState.NONE.halts());
    }

    @Test
    @DisplayName("diagnostics carry the position, which is the part that makes them actionable")
    void diagnosticsCarryPosition() {
        DiagnosticState state = DiagnosticState.of(FailureReason.NO_BLOCK, new BlockPos(3, -1, 2), 40);

        assertTrue(state.isFailure());
        assertEquals(new BlockPos(3, -1, 2), state.localPos());
        assertEquals(40, state.tick());
    }

    @Test
    @DisplayName("a null position degrades to the anchor rather than throwing")
    void nullPositionIsSafe() {
        assertEquals(BlockPos.ZERO, DiagnosticState.of(FailureReason.NO_CHARGE, null, 0).localPos());
    }

    @Test
    @DisplayName("round trips through its codec so a halt survives a reload")
    void roundTrips() {
        DiagnosticState original = DiagnosticState.of(FailureReason.INVENTORY_FULL, new BlockPos(-4, 2, 9), 120);

        var encoded = DiagnosticState.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError(msg));
        DiagnosticState decoded = DiagnosticState.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError(msg));

        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("every reason has a distinct serialized name and a translation key")
    void reasonsAreSerialisableAndPresentable() {
        long distinct = java.util.Arrays.stream(FailureReason.values())
                .map(FailureReason::getSerializedName)
                .distinct()
                .count();
        assertEquals(FailureReason.values().length, distinct);

        for (FailureReason reason : FailureReason.values()) {
            assertTrue(reason.translationKey().startsWith("diagnostic.chronoclones."));
        }
    }
}
