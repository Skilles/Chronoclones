package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingCodecs;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Chronoclones.MODID);

    /** The finished recording carried by a recorder in HOLDING state, or by an inscribed shard. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Recording>> RECORDING =
            COMPONENTS.registerComponentType("recording", builder -> builder
                    .persistent(RecordingCodecs.RECORDING)
                    .networkSynchronized(RecordingCodecs.RECORDING_STREAM));

    /**
     * Live counters while recording, for the HUD. Kept as a separate component so the (large)
     * recording payload is not rewritten every tick just to update a counter.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RecordingProgress>> PROGRESS =
            COMPONENTS.registerComponentType("recording_progress", builder -> builder
                    .persistent(RecordingProgress.CODEC)
                    .networkSynchronized(RecordingProgress.STREAM_CODEC));

    private ModDataComponents() {}
}
