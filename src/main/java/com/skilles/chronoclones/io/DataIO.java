package com.skilles.chronoclones.io;

import java.util.Optional;

import com.mojang.serialization.Codec;

//? if >=26 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

/** Adapters binding {@link DataOut}/{@link DataIn} to the active version's save primitives. */
public final class DataIO {

    private DataIO() {}

    //? if >=26 {
    public static DataOut wrap(ValueOutput output) {
        return new DataOut() {
            @Override
            public DataOut child(String key) {
                return wrap(output.child(key));
            }

            @Override
            public <T> void store(String key, Codec<T> codec, T value) {
                output.store(key, codec, value);
            }

            @Override
            public void putString(String key, String value) {
                output.putString(key, value);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                output.putBoolean(key, value);
            }

            @Override
            public void discard(String key) {
                output.discard(key);
            }
        };
    }

    public static DataIn wrap(ValueInput input) {
        return new DataIn() {
            @Override
            public Optional<DataIn> child(String key) {
                return input.child(key).map(DataIO::wrap);
            }

            @Override
            public <T> Optional<T> read(String key, Codec<T> codec) {
                return input.read(key, codec);
            }

            @Override
            public Optional<String> getString(String key) {
                return input.getString(key);
            }

            @Override
            public String getStringOr(String key, String fallback) {
                return input.getStringOr(key, fallback);
            }

            @Override
            public boolean getBooleanOr(String key, boolean fallback) {
                return input.getBooleanOr(key, fallback);
            }
        };
    }
    //?}
}
