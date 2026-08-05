//? if >=1.20.5 {
package com.skilles.chronoclones.registry;

import java.util.function.Supplier;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.platform.Registrar;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingCodecs;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModDataComponents {

    public static final Registrar<DataComponentType<?>> COMPONENTS =
            Registrar.create(BuiltInRegistries.DATA_COMPONENT_TYPE, Chronoclones.MODID);

    public static final Supplier<DataComponentType<Recording>> RECORDING =
            COMPONENTS.register("recording", () -> DataComponentType.<Recording>builder()
                    .persistent(RecordingCodecs.RECORDING)
                    .networkSynchronized(RecordingCodecs.RECORDING_STREAM)
                    .build());

    public static final Supplier<DataComponentType<RecordingProgress>> PROGRESS =
            COMPONENTS.register("recording_progress", () -> DataComponentType.<RecordingProgress>builder()
                    .persistent(RecordingProgress.CODEC)
                    .networkSynchronized(RecordingProgress.STREAM_CODEC)
                    .build());

    public static void init() {}

    private ModDataComponents() {}
}
//?}
