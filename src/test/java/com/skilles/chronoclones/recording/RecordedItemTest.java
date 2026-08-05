package com.skilles.chronoclones.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.RegistryAccess;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordedItemTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void captureRegistries() {
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    @DisplayName("a bare item id still decodes, so routines recorded before components keep working")
    void bareIdStillDecodes() {
        DataResult<RecordedItem> read = RecordedItem.CODEC.parse(
                registries.createSerializationContext(JsonOps.INSTANCE),
                new JsonPrimitive("minecraft:diamond_hoe"));

        RecordedItem item = read.getOrThrow();
        assertEquals(Items.DIAMOND_HOE, item.item().value());
        assertFalse(item.hasComponents(), "a bare id carries no components");
    }

    @Test
    @DisplayName("components survive a round trip")
    void componentsRoundTrip() {
        RecordedItem recorded = damaged(7);
        assertTrue(recorded.hasComponents(), "the damage should have been recorded");

        assertEquals(recorded, roundTrip(recorded));
    }

    @Test
    @DisplayName("an empty hand is representable, which vanilla's own item template is not")
    void emptyHandIsRepresentable() {
        assertTrue(RecordedItem.NOTHING.isEmpty());
        assertTrue(roundTrip(RecordedItem.NOTHING).isEmpty());
    }

    @Test
    @DisplayName("two recordings of the same item differ when their components do")
    void componentsDistinguish() {
        assertFalse(damaged(7).equals(damaged(9)),
                "an exact rule has to have something to tell these apart by");
        assertEquals(damaged(7), damaged(7));
    }

    private static RecordedItem roundTrip(RecordedItem item) {
        //? if >=1.20.5 {
        JsonElement written = RecordedItem.CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), item)
                .getOrThrow();
        return RecordedItem.CODEC
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), written)
                .getOrThrow();
        //?} else {
        /*// JSON narrows NBT numbers on the way back; this era stores tags, so ride NBT ops.
        net.minecraft.nbt.Tag written = RecordedItem.CODEC
                .encodeStart(net.minecraft.resources.RegistryOps.create(
                        net.minecraft.nbt.NbtOps.INSTANCE, registries), item)
                .getOrThrow(false, error -> {});
        return RecordedItem.CODEC
                .parse(net.minecraft.resources.RegistryOps.create(
                        net.minecraft.nbt.NbtOps.INSTANCE, registries), written)
                .getOrThrow(false, error -> {});
        *///?}
    }

    private static RecordedItem damaged(int damage) {
        //? if >=1.20.5 {
        return new RecordedItem(
                BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND_HOE),
                DataComponentPatch.builder().set(DataComponents.DAMAGE, damage).build());
        //?} else {
        /*net.minecraft.nbt.CompoundTag components = new net.minecraft.nbt.CompoundTag();
        components.putInt("Damage", damage);
        return new RecordedItem(
                BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND_HOE), components);
        *///?}
    }
}
