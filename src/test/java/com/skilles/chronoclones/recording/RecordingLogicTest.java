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

class RecordingLogicTest {

    private static <T> T unwrap(DataResult<T> result) {
        return result.getOrThrow(msg -> new AssertionError("codec failed: " + msg));
    }

    @Test
    @DisplayName("deleting an action leaves the others where they were")
    void deletingAnActionKeepsTheRest() {
        List<TimedAction> timed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            timed.add(new TimedAction(1 + i, new ChronoAction.UseOnBlock(
                    new net.minecraft.core.BlockPos(0, 0, -1 - i),
                    net.minecraft.core.Direction.UP, new Vec3(0.0, 0.5, 0.0), false,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(
                            net.minecraft.world.item.Items.AIR))));
        }
        Recording before = new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.copyOf(timed), 20, "Author", UUID.randomUUID());

        Recording after = before.without(1);

        assertEquals(2, after.actions().size());
        assertEquals(before.lengthTicks(), after.lengthTicks(),
                "deleting an action shortened the routine");
        assertEquals(before.actions().get(0).tick(), after.actions().get(0).tick(),
                "the surviving actions were re-timed by the deletion");
        assertEquals(before.actions().get(2).tick(), after.actions().get(1).tick(),
                "the surviving actions were re-timed by the deletion");
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
    @DisplayName("charge costs: break 10, place 5, attack 20")
    void chargeCostsMatchSpec() {
        assertEquals(10, ChronoActionType.BREAK_BLOCK.chargeCost());
        assertEquals(5, ChronoActionType.PLACE_BLOCK.chargeCost());
        assertEquals(20, ChronoActionType.ATTACK_ENTITY.chargeCost());
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
                        new MotionSample(2, new Vec3(1.0, 40.0, 0.0), 0f, 0f),
                        new MotionSample(4, new Vec3(-7.0, 2.0, 4.0), 0f, 0f)),
                List.of(), 100, "Skilles", UUID.randomUUID());

        assertEquals(Math.sqrt(65.0), r.reach(), 1.0e-9);
    }

    @Test
    @DisplayName("an empty recording reports zero reach rather than throwing")
    void emptyRecordingHasZeroReach() {
        Recording empty = new Recording(List.of(), List.of(), 0, "Skilles", UUID.randomUUID());
        assertEquals(0.0, empty.reach(), 0.0);
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("Recording defensively copies its lists, so the caller cannot mutate it afterwards")
    void listsAreDefensivelyCopied() {
        List<MotionSample> mutable = new ArrayList<>();
        mutable.add(new MotionSample(0, Vec3.ZERO, 0f, 0f));

        Recording r = new Recording(mutable, List.of(), 20, "Skilles", UUID.randomUUID());
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

        assertEquals(List.of("motion", "actions", "lengthTicks", "authorName", "authorId", "creative"),
                java.util.Arrays.stream(Recording.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList(),
                "Recording's fields changed: make sure the new one is not an owner");
    }

    private static Recording recording(int lengthTicks) {
        return new Recording(List.of(), List.of(), lengthTicks, "Skilles", UUID.randomUUID());
    }
}
