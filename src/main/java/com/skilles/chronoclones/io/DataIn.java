package com.skilles.chronoclones.io;

import java.util.Optional;

import com.mojang.serialization.Codec;

/** Read-side twin of {@link DataOut}. */
public interface DataIn {

    Optional<DataIn> child(String key);

    <T> Optional<T> read(String key, Codec<T> codec);

    Optional<String> getString(String key);

    String getStringOr(String key, String fallback);

    boolean getBooleanOr(String key, boolean fallback);
}
