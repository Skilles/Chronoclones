package com.skilles.chronoclones.replay;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which way the Chrono Lens points.
 *
 * <p>Small, because the rule is now small: without a lens a break goes ahead whatever is in the
 * square, and with one it goes ahead only for the recorded block. What that costs in practice — a
 * wooden pickaxe spending forever on obsidian rather than being told no — is in
 * {@code CoherenceGameTest}, where there is a world to mine.
 */
class CoherenceTest {

    private static Holder<Block> expect(Block block) {
        return BuiltInRegistries.BLOCK.wrapAsHolder(block);
    }

    private static BlockState found(Block block) {
        return block.defaultBlockState();
    }

    @Test
    @DisplayName("without a lens, whatever is in the square is fair game")
    void lenientAcceptsAnything() {
        assertTrue(Coherence.matches(found(Blocks.DEEPSLATE), expect(Blocks.STONE), Coherence.LENIENT));
        assertTrue(Coherence.matches(found(Blocks.OBSIDIAN), expect(Blocks.STONE), Coherence.LENIENT));
        assertTrue(Coherence.matches(found(Blocks.STONE), expect(Blocks.STONE), Coherence.LENIENT));
    }

    @Test
    @DisplayName("with a lens, only the recorded block")
    void exactAcceptsOnlyTheRecordedBlock() {
        assertTrue(Coherence.matches(found(Blocks.STONE), expect(Blocks.STONE), Coherence.EXACT));
        assertFalse(Coherence.matches(found(Blocks.DEEPSLATE), expect(Blocks.STONE), Coherence.EXACT));
    }

    @Test
    @DisplayName("a bare anchor is the lenient one")
    void lenientIsTheBareTier() {
        // Which way round this goes is the decision rather than an implementation detail: the strict
        // end is the one you go and craft, so an anchor nobody has upgraded does what it was shown.
        assertFalse(Coherence.isExact(
                com.skilles.chronoclones.block.UpgradeState.BASE.coherenceTier()));
    }
}
