package com.skilles.chronoclones.recording;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * How big a recording is allowed to be, asked of anything arriving from outside this server.
 *
 * <p>A recording is untrusted the moment it can be traded, edited into a save, or sent up by a
 * client. The action cap bounds how many things a routine does, but not how large they are: one
 * container session is a single action however many times it was clicked in, and an
 * {@link ItemStack} carries components of no fixed size. Both are checked here, and a recording
 * that fails either is refused rather than trimmed -- a routine that came back quietly different
 * from the one that was traded is worse than one that was plainly rejected.
 */
public final class RecordingLimits {

    private RecordingLimits() {}

    /**
     * Why a recording was refused, for the message shown to whoever offered it.
     */
    public enum Refusal {
        /** One container session carries more clicks than a session may. */
        TOO_MANY_STEPS("chronoclones.refused.steps"),
        /** The whole thing encodes to more bytes than a recording may. */
        TOO_LARGE("chronoclones.refused.size");

        private final String key;

        Refusal(String key) {
            this.key = key;
        }

        public String translationKey() {
            return key;
        }
    }

    /**
     * Whether this recording may be accepted, and why not if it may not.
     *
     * @param registries needed to encode item components; without them only the counts are checked
     * @return null if it is fine
     */
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

    /** True when this recording is small enough to accept. */
    public static boolean accepts(Recording recording, @Nullable RegistryAccess registries) {
        return refuse(recording, registries) == null;
    }

    /**
     * How many bytes this recording takes on the wire.
     *
     * <p>Measured by encoding it rather than estimated, because the parts that can grow without
     * bound are item components, whose size only their own codecs know. The buffer is released
     * either way: this runs on every imprint.
     */
    public static int encodedBytes(Recording recording, RegistryAccess registries) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            RecordingCodecs.RECORDING_STREAM.encode(buffer, recording);
            return buffer.writerIndex();
        } catch (RuntimeException encodingFailed) {
            // Something in here will not serialize. Whatever it is, it is not going in a save file.
            Chronoclones.LOGGER.warn("Could not measure a recording by {}; treating it as oversized",
                    recording.authorName(), encodingFailed);
            return Integer.MAX_VALUE;
        } finally {
            buffer.release();
        }
    }
}
