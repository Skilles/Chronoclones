package com.skilles.chronoclones.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.skilles.chronoclones.ChronoclonesConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingLimitsTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void captureRegistries() {
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    @DisplayName("an ordinary recording is accepted")
    void ordinaryIsAccepted() {
        assertNull(RecordingLimits.refuse(recordingWithSteps(4), registries));
    }

    @Test
    @DisplayName("a container session past the step cap is refused")
    void overfullSessionIsRefused() {
        int cap = ChronoclonesConfig.maxContainerSteps();
        assertEquals(RecordingLimits.Refusal.TOO_MANY_STEPS,
                RecordingLimits.refuse(recordingWithSteps(cap + 1), registries));
    }

    @Test
    @DisplayName("a session exactly at the cap is still allowed")
    void atTheCapIsAllowed() {
        int cap = ChronoclonesConfig.maxContainerSteps();
        assertNull(RecordingLimits.refuse(recordingWithSteps(cap), registries));
    }

    @Test
    @DisplayName("the step cap is checked without registries, because the codec has none")
    void stepCapNeedsNoRegistries() {
        int cap = ChronoclonesConfig.maxContainerSteps();
        assertEquals(RecordingLimits.Refusal.TOO_MANY_STEPS,
                RecordingLimits.refuse(recordingWithSteps(cap + 1), null));
    }

    @Test
    @DisplayName("an oversized recording does not decode, so it can never come back off a save")
    void oversizedDoesNotDecode() {
        int cap = ChronoclonesConfig.maxContainerSteps();
        DataResult<com.google.gson.JsonElement> written = RecordingCodecs.RECORDING
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),
                        recordingWithSteps(cap + 1));

        assertTrue(written.isError(), "an oversized recording should not encode");
    }

    @Test
    @DisplayName("measuring a recording reports something plausible rather than throwing")
    void measuresEncodedSize() {
        int small = RecordingLimits.encodedBytes(recordingWithSteps(1), registries);
        int large = RecordingLimits.encodedBytes(recordingWithSteps(64), registries);
        assertTrue(small > 0, "a recording occupies some bytes");
        assertTrue(large > small, "more clicks should encode to more bytes, got "
                + large + " against " + small);
    }

    private static Recording recordingWithSteps(int steps) {
        List<SessionStep> clicks = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            clicks.add(new SessionStep.Move(0, 1,
                    BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND), SessionStep.Amount.ALL));
        }

        ChronoAction.UseContainer session = new ChronoAction.UseContainer(
                new MenuTarget.Block(BlockPos.ZERO, Optional.empty()),
                27, List.of(), clicks);

        return new Recording(
                List.of(new MotionSample(0, Vec3.ZERO, 0f, 0f)),
                List.of(new TimedAction(1, session, ActionSettings.DEFAULT)),
                20, "Tester", UUID.nameUUIDFromBytes("tester".getBytes()));
    }
}
