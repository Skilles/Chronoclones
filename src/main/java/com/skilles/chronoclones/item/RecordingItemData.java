package com.skilles.chronoclones.item;

import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.registry.RecordingProgress;
//? if >=1.20.5 {
import com.skilles.chronoclones.registry.ModDataComponents;
//?}

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Where a recorder or shard keeps its recording and progress stamp: a data component in the
 * component era, the item tag before that.
 */
public final class RecordingItemData {

    private RecordingItemData() {}

    //? if >=1.20.5 {
    public static @Nullable Recording recording(ItemStack stack) {
        return stack.get(ModDataComponents.RECORDING.get());
    }

    public static boolean hasRecording(ItemStack stack) {
        return stack.has(ModDataComponents.RECORDING.get());
    }

    public static void setRecording(ItemStack stack, Recording recording) {
        stack.set(ModDataComponents.RECORDING.get(), recording);
    }

    public static void clearRecording(ItemStack stack) {
        stack.remove(ModDataComponents.RECORDING.get());
    }

    public static @Nullable RecordingProgress progress(ItemStack stack) {
        return stack.get(ModDataComponents.PROGRESS.get());
    }

    public static boolean hasProgress(ItemStack stack) {
        return stack.has(ModDataComponents.PROGRESS.get());
    }

    public static void setProgress(ItemStack stack, RecordingProgress progress) {
        stack.set(ModDataComponents.PROGRESS.get(), progress);
    }

    public static void clearProgress(ItemStack stack) {
        stack.remove(ModDataComponents.PROGRESS.get());
    }
    //?} else {
    /*private static final String RECORDING_KEY = "chronoclones:recording";
    private static final String PROGRESS_KEY = "chronoclones:recording_progress";

    public static @Nullable Recording recording(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        net.minecraft.nbt.Tag stored = tag.get(RECORDING_KEY);
        if (stored == null) {
            // A dropped anchor carries its routine inside the block-entity tag instead.
            stored = tag.getCompound("BlockEntityTag").get("recording");
        }
        if (stored == null) {
            return null;
        }
        return com.skilles.chronoclones.recording.RecordingCodecs.RECORDING
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, stored)
                .result().orElse(null);
    }

    public static boolean hasRecording(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(RECORDING_KEY);
    }

    public static void setRecording(ItemStack stack, Recording recording) {
        com.skilles.chronoclones.recording.RecordingCodecs.RECORDING.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, recording).result()
                .ifPresent(encoded -> stack.getOrCreateTag().put(RECORDING_KEY, encoded));
    }

    public static void clearRecording(ItemStack stack) {
        clearKey(stack, RECORDING_KEY);
    }

    public static @Nullable RecordingProgress progress(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(PROGRESS_KEY)) {
            return null;
        }
        return RecordingProgress.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get(PROGRESS_KEY))
                .result().orElse(null);
    }

    public static boolean hasProgress(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(PROGRESS_KEY);
    }

    public static void setProgress(ItemStack stack, RecordingProgress progress) {
        RecordingProgress.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, progress).result()
                .ifPresent(encoded -> stack.getOrCreateTag().put(PROGRESS_KEY, encoded));
    }

    public static void clearProgress(ItemStack stack) {
        clearKey(stack, PROGRESS_KEY);
    }

    private static void clearKey(ItemStack stack, String key) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(key);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        }
    }
    *///?}
}
