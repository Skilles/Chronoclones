package com.skilles.chronoclones.recording;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The item an action was recorded with, as it was: kind and components together.
 *
 * <p>A recording used to keep only the kind, so a routine could not tell a healing potion from a
 * harming one, a charged crossbow from an empty one, or a firework of one flight duration from
 * another. What it reached for at replay was whichever of them happened to be lying in the anchor.
 *
 * <p>Not vanilla's {@link net.minecraft.world.item.ItemStackTemplate}, which is the same idea and
 * would otherwise be exactly right: it refuses to represent an empty item at all, and half the
 * interactions worth recording -- flipping a lever, opening a door, shearing a sheep bare-handed --
 * are done with nothing in hand.
 */
public record RecordedItem(Holder<Item> item, DataComponentPatch components) {

    /** An action performed with an empty hand, which needs nothing lent to it. */
    public static final RecordedItem NOTHING =
            new RecordedItem(Items.AIR.builtInRegistryHolder(), DataComponentPatch.EMPTY);

    private static final MapCodec<RecordedItem> FULL = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(RecordedItem::item),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(RecordedItem::components)
    ).apply(i, RecordedItem::new));

    /**
     * Reads either shape.
     *
     * <p>A bare item id is what every routine recorded before components were kept wrote, and it
     * still means what it meant: that kind of thing, with nothing particular about it.
     */
    public static final Codec<RecordedItem> CODEC = Codec.withAlternative(
            FULL.codec(),
            BuiltInRegistries.ITEM.holderByNameCodec(),
            item -> new RecordedItem(item, DataComponentPatch.EMPTY));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordedItem> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(net.minecraft.core.registries.Registries.ITEM),
                    RecordedItem::item,
                    DataComponentPatch.STREAM_CODEC, RecordedItem::components,
                    RecordedItem::new);

    /** What the player was holding, or {@link #NOTHING} for an empty hand. */
    public static RecordedItem of(ItemStack stack) {
        return stack.isEmpty()
                ? NOTHING
                : new RecordedItem(stack.typeHolder(), stack.getComponentsPatch());
    }

    /** The same, by kind alone. */
    public static RecordedItem of(Holder<Item> item) {
        return new RecordedItem(item, DataComponentPatch.EMPTY);
    }

    public boolean isEmpty() {
        return item.value() == Items.AIR;
    }

    /** One of it, components and all, for display and for handing to the world. */
    public ItemStack create() {
        return isEmpty() ? ItemStack.EMPTY : new ItemStack(item, 1, components);
    }

    /** For a diagnostic that wants to name the thing without building one. */
    public Identifier id() {
        return BuiltInRegistries.ITEM.getKey(item.value());
    }
}
