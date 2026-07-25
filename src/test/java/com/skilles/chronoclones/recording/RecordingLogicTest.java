package com.skilles.chronoclones.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The parts of the recording model that can be asserted without a bootstrapped registry.
 *
 * <p>Anything involving {@code Holder<Block>}, {@code ItemStack} or {@code BlockState} needs the
 * game's registries to exist, which plain JUnit cannot do against a NeoForge-patched jar — see
 * {@link RecordingCodecTest} for that half and why it is currently parked.
 */
class RecordingLogicTest {

    private static <T> T unwrap(DataResult<T> result) {
        return result.getOrThrow(msg -> new AssertionError("codec failed: " + msg));
    }

    @Test
    @DisplayName("action type serialises by name, so reordering the enum cannot reinterpret saved data")
    void actionTypeIsSerialisedByName() {
        for (ChronoActionType type : ChronoActionType.values()) {
            String name = unwrap(ChronoActionType.CODEC.encodeStart(JsonOps.INSTANCE, type)).getAsString();
            assertEquals(type.getSerializedName(), name);
            assertEquals(type, unwrap(ChronoActionType.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(name))));
        }
    }

    @Test
    @DisplayName("every action type has a distinct serialized name")
    void actionTypeNamesAreDistinct() {
        long distinct = java.util.Arrays.stream(ChronoActionType.values())
                .map(ChronoActionType::getSerializedName)
                .distinct()
                .count();
        assertEquals(ChronoActionType.values().length, distinct);
    }

    @Test
    @DisplayName("charge costs match the spec: break 10, place 5, attack 20")
    void chargeCostsMatchSpec() {
        assertEquals(10, ChronoActionType.BREAK_BLOCK.chargeCost());
        assertEquals(5, ChronoActionType.PLACE_BLOCK.chargeCost());
        assertEquals(20, ChronoActionType.ATTACK_ENTITY.chargeCost());
    }

    @Test
    @DisplayName("fidelity tiers gate actions in the spec's order: BREAK -> PLACE -> ATTACK -> USE")
    void fidelityTiersAreOrdered() {
        assertTrue(ChronoActionType.BREAK_BLOCK.fidelityTier() < ChronoActionType.PLACE_BLOCK.fidelityTier());
        assertTrue(ChronoActionType.PLACE_BLOCK.fidelityTier() < ChronoActionType.ATTACK_ENTITY.fidelityTier());
        assertTrue(ChronoActionType.ATTACK_ENTITY.fidelityTier() < ChronoActionType.USE_ITEM.fidelityTier());
    }

    @Test
    @DisplayName("the fidelity gate admits exactly the tiers at or below the upgrade level")
    void fidelityGateAdmitsCorrectTypes() {
        // Tier 0: a fresh anchor mines and nothing else.
        assertTrue(ChronoActionType.BREAK_BLOCK.permittedAt(0));
        assertFalse(ChronoActionType.PLACE_BLOCK.permittedAt(0));
        assertFalse(ChronoActionType.ATTACK_ENTITY.permittedAt(0));
        assertFalse(ChronoActionType.USE_ITEM.permittedAt(0));

        // Tier 1 adds placing, still no combat — the spec gates combat behind progression.
        assertTrue(ChronoActionType.BREAK_BLOCK.permittedAt(1));
        assertTrue(ChronoActionType.PLACE_BLOCK.permittedAt(1));
        assertFalse(ChronoActionType.ATTACK_ENTITY.permittedAt(1));

        // Tier 2 adds combat.
        assertTrue(ChronoActionType.ATTACK_ENTITY.permittedAt(2));
        assertFalse(ChronoActionType.USE_ITEM.permittedAt(2));

        // Tier 3 admits everything.
        for (ChronoActionType type : ChronoActionType.values()) {
            assertTrue(type.permittedAt(3), type + " should be permitted at the top tier");
        }
    }

    @Test
    @DisplayName("length in seconds is ticks / 20")
    void lengthSeconds() {
        assertEquals(10, recording(200).lengthSeconds());
        assertEquals(0, recording(19).lengthSeconds());
        assertEquals(30, recording(600).lengthSeconds());
    }

    @Test
    @DisplayName("reach measures the furthest horizontal distance, ignoring vertical extent")
    void reachIsHorizontalOnly() {
        Recording r = new Recording(
                List.of(
                        new MotionSample(0, new Vec3(0.0, 0.0, 0.0), 0f, 0f),
                        // Tall but close: must not count as reach.
                        new MotionSample(2, new Vec3(1.0, 40.0, 0.0), 0f, 0f),
                        new MotionSample(4, new Vec3(-7.0, 2.0, 4.0), 0f, 0f)),
                List.of(), 100, "Bilal", UUID.randomUUID());

        assertEquals(Math.sqrt(65.0), r.reach(), 1.0e-9);
    }

    @Test
    @DisplayName("an empty recording reports zero reach rather than throwing")
    void emptyRecordingHasZeroReach() {
        Recording empty = new Recording(List.of(), List.of(), 0, "Bilal", UUID.randomUUID());
        assertEquals(0.0, empty.reach(), 0.0);
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("Recording defensively copies its lists, so the caller cannot mutate it afterwards")
    void listsAreDefensivelyCopied() {
        List<MotionSample> mutable = new ArrayList<>();
        mutable.add(new MotionSample(0, Vec3.ZERO, 0f, 0f));

        Recording r = new Recording(mutable, List.of(), 20, "Bilal", UUID.randomUUID());
        mutable.add(new MotionSample(2, new Vec3(9, 9, 9), 0f, 0f));

        assertEquals(1, r.motion().size());
    }

    @Test
    @DisplayName("author identity is carried on the recording and never an owner field")
    void recordingCarriesAuthorNotOwner() {
        UUID author = UUID.randomUUID();
        Recording r = new Recording(List.of(), List.of(), 20, "Author", author);

        assertEquals(author, r.authorId());
        assertEquals("Author", r.authorName());

        // ownership lives on the block entity. If a setter or field for it ever appears
        // on Recording, the author/owner split has been collapsed and attribution is a griefing bug.
        assertEquals(5, Recording.class.getRecordComponents().length,
                "Recording gained a component — make sure it is not an owner field");
    }

    private static Recording recording(int lengthTicks) {
        return new Recording(List.of(), List.of(), lengthTicks, "Bilal", UUID.randomUUID());
    }
}
