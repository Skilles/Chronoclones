package com.skilles.chronoclones.recording;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** How big a recording may be, asked of anything arriving from outside this server. */
public final class RecordingLimits {

    private RecordingLimits() {}

    public enum Refusal {

        TOO_MANY_STEPS("chronoclones.refused.steps"),
        TOO_LARGE("chronoclones.refused.size");

        private final String key;

        Refusal(String key) {
            this.key = key;
        }

        public String translationKey() {
            return key;
        }
    }

    public static @Nullable Refusal refuse(Recording recording, @Nullable RegistryAccess registries) {
        int cap = ChronoclonesConfig.maxContainerSteps();
        for (TimedAction timed : recording.actions()) {
            if (timed.action() instanceof ChronoAction.UseContainer session
                    && session.steps().size() > cap) {
                Chronoclones.LOGGER.warn(
                        "Refusing a recording by {}: a container session has {} steps, the cap is {}",
                        recording.authorName(), session.steps().size(), cap);
                return Refusal.TOO_MANY_STEPS;
            }
        }

        if (registries == null) {
            return null;
        }
        int bytes = encodedBytes(recording, registries);
        int limit = ChronoclonesConfig.maxRecordingBytes();
        if (bytes > limit) {
            Chronoclones.LOGGER.warn("Refusing a recording by {}: it encodes to {} bytes, the cap is {}",
                    recording.authorName(), bytes, limit);
            return Refusal.TOO_LARGE;
        }
        return null;
    }

    public static boolean accepts(Recording recording, @Nullable RegistryAccess registries) {
        return refuse(recording, registries) == null;
    }

    public static int encodedBytes(Recording recording, RegistryAccess registries) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            RecordingCodecs.RECORDING_STREAM.encode(buffer, recording);
            return buffer.writerIndex();
        } catch (RuntimeException encodingFailed) {
            Chronoclones.LOGGER.warn("Could not measure a recording by {}; treating it as oversized",
                    recording.authorName(), encodingFailed);
            return Integer.MAX_VALUE;
        } finally {
            buffer.release();
        }
    }
}
