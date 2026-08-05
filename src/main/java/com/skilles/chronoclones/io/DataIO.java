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
    //?} else {
    /*// Pre-26 versions save straight into NBT; codecs run through registry-aware ops.
    public static DataOut wrap(net.minecraft.nbt.CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops =
                net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries);
        return new DataOut() {
            @Override
            public DataOut child(String key) {
                net.minecraft.nbt.CompoundTag existing = tag.getCompound(key);
                tag.put(key, existing);
                return wrap(existing, registries);
            }

            @Override
            public <T> void store(String key, Codec<T> codec, T value) {
                codec.encodeStart(ops, value).result().ifPresent(element -> tag.put(key, element));
            }

            @Override
            public void putString(String key, String value) {
                tag.putString(key, value);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                tag.putBoolean(key, value);
            }

            @Override
            public void discard(String key) {
                tag.remove(key);
            }
        };
    }

    public static DataIn wrap(net.minecraft.nbt.CompoundTag tag,
                              net.minecraft.core.HolderLookup.Provider registries,
                              // Overload-disambiguation marker; pre-26 CompoundTag is one type
                              // for both directions where ValueInput and ValueOutput are two.
                              boolean read) {
        com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops =
                net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries);
        return new DataIn() {
            @Override
            public Optional<DataIn> child(String key) {
                return tag.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)
                        ? Optional.of(wrap(tag.getCompound(key), registries, true))
                        : Optional.empty();
            }

            @Override
            public <T> Optional<T> read(String key, Codec<T> codec) {
                net.minecraft.nbt.Tag element = tag.get(key);
                return element == null ? Optional.empty() : codec.parse(ops, element).result();
            }

            @Override
            public Optional<String> getString(String key) {
                return tag.contains(key, net.minecraft.nbt.Tag.TAG_STRING)
                        ? Optional.of(tag.getString(key))
                        : Optional.empty();
            }

            @Override
            public String getStringOr(String key, String fallback) {
                return getString(key).orElse(fallback);
            }

            @Override
            public boolean getBooleanOr(String key, boolean fallback) {
                return tag.contains(key, net.minecraft.nbt.Tag.TAG_BYTE)
                        ? tag.getBoolean(key)
                        : fallback;
            }
        };
    }
    *///?}
}
