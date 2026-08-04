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
 * The item an action was recorded with, kind and components together.
 *
 * <p>Not vanilla's ItemStackTemplate, which cannot represent an empty hand.
 */
public record RecordedItem(Holder<Item> item, DataComponentPatch components) {

    public static final RecordedItem NOTHING =
            new RecordedItem(Items.AIR.builtInRegistryHolder(), DataComponentPatch.EMPTY);

    private static final MapCodec<RecordedItem> FULL = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(RecordedItem::item),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(RecordedItem::components)
    ).apply(i, RecordedItem::new));

    /** A bare item id is what routines recorded before components were kept still contain. */
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

    public static RecordedItem of(ItemStack stack) {
        return stack.isEmpty()
                ? NOTHING
                : new RecordedItem(stack.typeHolder(), stack.getComponentsPatch());
    }

    public static RecordedItem of(Holder<Item> item) {
        return new RecordedItem(item, DataComponentPatch.EMPTY);
    }

    public boolean isEmpty() {
        return item.value() == Items.AIR;
    }

    /** Whether the template carried anything beyond the bare item. */
    public boolean hasComponents() {
        return !components.isEmpty();
    }

    /** EXACT-rule equality: the same recorded extras, however the era stores them. */
    public boolean matchesComponentsOf(RecordedItem other) {
        return components.equals(other.components);
    }

    public ItemStack create() {
        return isEmpty() ? ItemStack.EMPTY : new ItemStack(item, 1, components);
    }

    public Identifier id() {
        return BuiltInRegistries.ITEM.getKey(item.value());
    }
}
