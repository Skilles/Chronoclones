//? if <1.20.5 {
/*package com.skilles.chronoclones.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

// The slice of 1.20.5's ByteBufCodecs that the mod uses, for versions that predate it.
public final class ByteBufCodecs {

    private ByteBufCodecs() {}

    private static FriendlyByteBuf friendly(Object buffer) {
        ByteBuf raw = (ByteBuf) buffer;
        return raw instanceof FriendlyByteBuf friendlyBuf ? friendlyBuf : new FriendlyByteBuf(raw);
    }

    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(
            (buffer, value) -> buffer.writeBoolean(value), ByteBuf::readBoolean);

    public static final StreamCodec<ByteBuf, Integer> INT = StreamCodec.of(
            (buffer, value) -> buffer.writeInt(value), ByteBuf::readInt);

    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of(
            (buffer, value) -> friendly(buffer).writeVarInt(value),
            buffer -> friendly(buffer).readVarInt());

    public static final StreamCodec<ByteBuf, Float> FLOAT = StreamCodec.of(
            (buffer, value) -> buffer.writeFloat(value), ByteBuf::readFloat);

    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of(
            (buffer, value) -> buffer.writeDouble(value), ByteBuf::readDouble);

    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = StreamCodec.of(
            (buffer, value) -> friendly(buffer).writeUtf(value),
            buffer -> friendly(buffer).readUtf());

    public static final StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = StreamCodec.of(
            (buffer, value) -> friendly(buffer).writeByteArray(value),
            buffer -> friendly(buffer).readByteArray());

    public static final StreamCodec<ByteBuf, com.mojang.authlib.GameProfile> GAME_PROFILE =
            StreamCodec.of(
                    (buffer, value) -> friendly(buffer).writeGameProfile(value),
                    buffer -> friendly(buffer).readGameProfile());

    public static <B, V> StreamCodec<B, Optional<V>> optional(StreamCodec<? super B, V> codec) {
        return StreamCodec.of((buffer, value) -> {
            friendly(buffer).writeBoolean(value.isPresent());
            value.ifPresent(present -> codec.encode(buffer, present));
        }, buffer -> friendly(buffer).readBoolean()
                ? Optional.of(codec.decode(buffer))
                : Optional.empty());
    }

    public static <V> StreamCodec<ByteBuf, V> idMapper(IntFunction<V> byId, ToIntFunction<V> toId) {
        return StreamCodec.of(
                (buffer, value) -> friendly(buffer).writeVarInt(toId.applyAsInt(value)),
                buffer -> byId.apply(friendly(buffer).readVarInt()));
    }

    public static <B, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(
            IntFunction<C> factory) {
        return codec -> StreamCodec.of((buffer, value) -> {
            friendly(buffer).writeVarInt(value.size());
            for (V element : value) {
                codec.encode(buffer, element);
            }
        }, buffer -> {
            int size = friendly(buffer).readVarInt();
            C out = factory.apply(size);
            for (int index = 0; index < size; index++) {
                out.add(codec.decode(buffer));
            }
            return out;
        });
    }

    public static <B, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> ByteBufCodecs.<B, V, List<V>>collection(ArrayList::new).apply(codec);
    }

    // Serialized through NBT under one key, with registry-aware ops when the buffer has them.
    public static <V> StreamCodec<ByteBuf, V> fromCodec(Codec<V> codec) {
        return StreamCodec.of((buffer, value) -> {
            CompoundTag holder = new CompoundTag();
            holder.put("v", codec.encodeStart(opsFor(buffer), value)
                    .getOrThrow(false, error -> {
                        throw new io.netty.handler.codec.EncoderException(error);
                    }));
            friendly(buffer).writeNbt(holder);
        }, buffer -> {
            CompoundTag holder = friendly(buffer).readNbt();
            if (holder == null) {
                throw new io.netty.handler.codec.DecoderException("missing nbt payload");
            }
            return codec.parse(opsFor(buffer), holder.get("v"))
                    .getOrThrow(false, error -> {
                        throw new io.netty.handler.codec.DecoderException(error);
                    });
        });
    }

    private static com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> opsFor(ByteBuf buffer) {
        return buffer instanceof RegistryFriendlyByteBuf registry
                ? RegistryOps.create(NbtOps.INSTANCE, registry.registryAccess())
                : NbtOps.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public static <T> StreamCodec<ByteBuf, Holder<T>> holderRegistry(
            ResourceKey<? extends Registry<T>> registryKey) {
        return StreamCodec.of((buffer, value) -> {
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY
                    .get(registryKey.location());
            friendly(buffer).writeVarInt(registry.getId(value.value()));
        }, buffer -> {
            Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY
                    .get(registryKey.location());
            int id = friendly(buffer).readVarInt();
            return registry.getHolder(id).orElseThrow(
                    () -> new io.netty.handler.codec.DecoderException(
                            "unknown id " + id + " in " + registryKey));
        });
    }
}
*///?}
