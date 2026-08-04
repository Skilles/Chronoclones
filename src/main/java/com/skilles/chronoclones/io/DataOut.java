package com.skilles.chronoclones.io;

import com.mojang.serialization.Codec;

/**
 * The slice of a save-output surface the mod writes through. 26.x backs it with vanilla
 * {@code ValueOutput}; older versions back it with a {@code CompoundTag}, so the save code
 * itself never changes shape across targets.
 */
public interface DataOut {

    DataOut child(String key);

    <T> void store(String key, Codec<T> codec, T value);

    void putString(String key, String value);

    void putBoolean(String key, boolean value);

    void discard(String key);
}
