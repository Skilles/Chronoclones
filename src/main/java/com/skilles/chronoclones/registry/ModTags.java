package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {

    /**
     * Blocks a clone may never break, whatever the recording says.
     */
    public static final TagKey<Block> ANCHOR_UNBREAKABLE =
            TagKey.create(Registries.BLOCK, Chronoclones.id("anchor_unbreakable"));

    private ModTags() {}
}
