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
 * The structure of the coherence rule: identity, and which way the lens points.
 *
 * <p><b>The tool half is deliberately not asserted here.</b> Whether a pickaxe can harvest a block
 * runs through {@code requiresCorrectToolForDrops} and the tool's own tier, both of which depend on
 * datapack-loaded tags — and the FML JUnit bootstrap loads mods but not a world, so every tag query
 * in this environment answers false. A test claiming "a diamond pickaxe reaches obsidian" would pass
 * for the wrong reason today and keep passing if the rule were deleted tomorrow. Those live in
 * {@code CoherenceGameTest}, inside a running server.
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
                Coherence.LENIENT, ItemStack.EMPTY));
        assertTrue(Coherence.matches(found(Blocks.STONE), expect(Blocks.STONE),
                Coherence.EXACT, ItemStack.EMPTY));
    }

    @Test
    @DisplayName("an anchor with a lens never substitutes, however good the tool")
    void exactNeverSubstitutes() {
        // The point of fitting a lens: the block has to be the recorded one, full stop.
        assertFalse(Coherence.matches(found(Blocks.DEEPSLATE), expect(Blocks.STONE),
                Coherence.EXACT, ItemStack.EMPTY));
        assertFalse(Coherence.matches(found(Blocks.DIRT), expect(Blocks.STONE),
                Coherence.EXACT, ItemStack.EMPTY));
    }

    @Test
    @DisplayName("lenient is the default an anchor starts with")
    void lenientIsTheBareTier() {
        // The tiers run opposite to the spec's, so this is worth pinning down: a fresh anchor is
        // forgiving, and precision is the thing you have to go and craft.
        assertFalse(Coherence.isExact(Coherence.LENIENT),
                "a bare anchor must be the lenient one — a strict default silently does nothing");
        assertTrue(Coherence.isExact(Coherence.EXACT));
        assertFalse(Coherence.isExact(com.skilles.chronoclones.block.UpgradeState.BASE.coherenceTier()));
    }
}
