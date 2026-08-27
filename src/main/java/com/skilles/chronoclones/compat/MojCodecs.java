//? if <1.20.5 {
/*package com.skilles.chronoclones.compat;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

// Stream codecs for vanilla types that only gained their own constants in 1.20.5.
public final class MojCodecs {

    private MojCodecs() {}

    private static FriendlyByteBuf friendly(ByteBuf buffer) {
        return buffer instanceof FriendlyByteBuf friendlyBuf ? friendlyBuf : new FriendlyByteBuf(buffer);
    }

    public static final StreamCodec<ByteBuf, BlockPos> BLOCK_POS = StreamCodec.of(
            (buffer, value) -> friendly(buffer).writeBlockPos(value),
            buffer -> friendly(buffer).readBlockPos());

    public static final StreamCodec<ByteBuf, Direction> DIRECTION = ByteBufCodecs.idMapper(
            id -> Direction.values()[id], Enum::ordinal);

    public static final StreamCodec<ByteBuf, java.util.UUID> UUID_STREAM = StreamCodec.of(
            (buffer, value) -> friendly(buffer).writeUUID(value),
            buffer -> friendly(buffer).readUUID());

    public static final StreamCodec<ByteBuf, ItemStack> ITEM_STACK = StreamCodec.of(
            (buffer, value) -> friendly(buffer).writeItem(value),
            buffer -> friendly(buffer).readItem());

    public static final StreamCodec<ByteBuf, ItemStack> OPTIONAL_ITEM_STACK = ITEM_STACK;
}
*///?}
