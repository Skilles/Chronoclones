package com.skilles.chronoclones.replay;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structure of the coherence rule: identity, tier gating, and the shape of the group list.
 *
 * <p><b>Tag membership is deliberately not asserted here.</b> Block tags come from datapacks, and the
 * FML JUnit bootstrap loads mods but not a world — so every {@code is(TagKey)} call in this
 * environment answers false, and a test claiming "deepslate stands in for stone" would pass for the
 * wrong reason today and keep passing if the rule were deleted tomorrow. The substitutions that
 * actually matter are asserted in {@code CoherenceGameTest}, inside a running server.
 *
 * <p>What is worth checking here is everything that holds regardless of tags: that an exact match
 * never depends on the tier, and that a mismatch with nothing to match on is refused at both.
 */
class CoherenceTest {

    private static final List<TagKey<Block>> GROUPS = Coherence.defaultGroups();

    private static Holder<Block> expect(Block block) {
        return BuiltInRegistries.BLOCK.wrapAsHolder(block);
    }

    private static BlockState found(Block block) {
        return block.defaultBlockState();
    }

    @Test
    @DisplayName("an exact match is accepted at every tier")
    void identityAlwaysMatches() {
        assertTrue(Coherence.matches(found(Blocks.STONE), expect(Blocks.STONE), Coherence.STRICT, GROUPS));
        assertTrue(Coherence.matches(found(Blocks.STONE), expect(Blocks.STONE), Coherence.LOOSE, GROUPS));
    }

    @Test
    @DisplayName("with no group to share, a different block is refused at either tier")
    void mismatchWithoutAGroupIsRefused() {
        // An empty list stands in for "these two blocks have nothing in common" without depending on
        // what any particular tag contains.
        assertFalse(Coherence.matches(found(Blocks.DEEPSLATE), expect(Blocks.STONE),
                Coherence.STRICT, List.of()));
        assertFalse(Coherence.matches(found(Blocks.DEEPSLATE), expect(Blocks.STONE),
                Coherence.LOOSE, List.of()));
    }

    @Test
    @DisplayName("the shipped groups parse, and exclude the broad mineable tags")
    void defaultGroupsAreNarrow() {
        assertFalse(GROUPS.isEmpty(), "a lens with no groups configured would do nothing at all");

        // The whole reason this is a named list rather than "any shared tag": every stone-like block
        // is mineable with a pickaxe, so that tag would let a stone-clearing routine accept a base.
        TagKey<Block> pickaxe = TagKey.create(Registries.BLOCK,
                Identifier.withDefaultNamespace("mineable/pickaxe"));
        assertFalse(GROUPS.contains(pickaxe), "shipping mineable/pickaxe as a group is a griefing bug");
    }
}
