package com.skilles.chronoclones.replay;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structure of the coherence rule: identity and tier gating.
 *
 * <p><b>The tool half is deliberately not asserted here.</b> Whether a pickaxe can harvest a block
 * runs through {@code requiresCorrectToolForDrops} and the tool's own tier, both of which depend on
 * datapack-loaded tags — and the FML JUnit bootstrap loads mods but not a world, so every tag query
 * in this environment answers false. A test claiming "a diamond pickaxe reaches obsidian" would pass
 * for the wrong reason today and keep passing if the rule were deleted tomorrow. Those live in
 * {@code CoherenceGameTest}, inside a running server.
 *
 * <p>What holds regardless of tags is asserted here: an exact match never depends on the tier, and a
 * bare anchor never substitutes anything.
 */
class CoherenceTest {

    private static Holder<Block> expect(Block block) {
        return BuiltInRegistries.BLOCK.wrapAsHolder(block);
    }

    private static BlockState found(Block block) {
        return block.defaultBlockState();
    }

    @Test
    @DisplayName("an exact match is accepted at every tier, with any tool")
    void identityAlwaysMatches() {
        assertTrue(Coherence.matches(found(Blocks.STONE), expect(Blocks.STONE),
                Coherence.STRICT, ItemStack.EMPTY));
        assertTrue(Coherence.matches(found(Blocks.STONE), expect(Blocks.STONE),
                Coherence.LOOSE, ItemStack.EMPTY));
    }

    @Test
    @DisplayName("a bare anchor never substitutes, however good the tool")
    void strictNeverSubstitutes() {
        // The whole of the base behaviour: without a lens, the block has to be the recorded one.
        assertFalse(Coherence.matches(found(Blocks.DEEPSLATE), expect(Blocks.STONE),
                Coherence.STRICT, ItemStack.EMPTY));
        assertFalse(Coherence.matches(found(Blocks.DIRT), expect(Blocks.STONE),
                Coherence.STRICT, ItemStack.EMPTY));
    }
}
