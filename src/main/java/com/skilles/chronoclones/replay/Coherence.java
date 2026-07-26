package com.skilles.chronoclones.replay;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How closely the world has to match the recording for a break to go ahead.
 *
 * <p>This governs exactly one decision — what a {@code BreakBlock} accepts at its target square — and
 * that is not an oversight. Attacks already treat the recorded entity type as a hint and fall back to
 * the nearest living thing; placement checks whether the target is replaceable rather than what it
 * is; using an item on a block checks nothing at all. Breaking was the only action strict enough to
 * need loosening.
 *
 * <h2>Why not "shares any tag"</h2>
 *
 * <p>The spec's wording for {@code LOOSE} is "any block sharing a tag with the expected block". Taken
 * literally that is a griefing footgun: every stone-like block is in {@code #minecraft:mineable/pickaxe},
 * so a routine recorded to clear stone would happily accept the walls of your base, your ores, and
 * whatever else is nearby. The tags that make a useful equivalence — stone to deepslate, oak to birch
 * — are a small named set, and which ones they are is a pack decision rather than ours.
 *
 * <p>Hence {@link ChronoclonesConfig#COHERENCE_GROUPS}, a list of tag ids, rather than a datapack tag.
 * Block tags flatten: a single tag-of-tags containing {@code #logs} and {@code #base_stone_overworld}
 * would resolve to one flat set in which oak and deepslate are members of the same thing, and "shares
 * a group" would then accept oak for stone. Keeping the groups as separate tags preserves exactly the
 * structure the rule needs.
 */
public final class Coherence {

    private Coherence() {}

    /** Exact match only. What a bare anchor does. */
    public static final int STRICT = 0;
    /** Accepts a block from the same equivalence group. One Chrono Lens. */
    public static final int LOOSE = 1;

    /**
     * Whether the block that is actually there is close enough to the one the recording expected.
     *
     * <p>Note that the drop calculation still uses the recorded {@code toolTemplate}. A routine
     * recorded with a wooden pickaxe against stone will, at {@code LOOSE}, break the deepslate that
     * replaced it and get nothing for it — the match is about what to act on, not about whether
     * acting is worthwhile.
     */
    public static boolean matches(BlockState candidate, Holder<Block> expected, int tier) {
        return matches(candidate, expected, tier, configuredGroups());
    }

    /**
     * The rule itself, against an explicit group list.
     *
     * <p>Split out so it can be tested: the config is only bound inside a running server, and the
     * assertions worth making here are about which substitutions the rule allows, not about where
     * the list came from.
     */
    public static boolean matches(BlockState candidate, Holder<Block> expected, int tier,
                                  List<TagKey<Block>> groups) {
        if (candidate.getBlock().equals(expected.value())) {
            return true;
        }
        if (tier < LOOSE) {
            return false;
        }
        for (TagKey<Block> group : groups) {
            // Both, in the *same* group. Membership in some group each is not enough, or oak would
            // stand in for deepslate.
            if (candidate.typeHolder().is(group) && expected.is(group)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The configured equivalence groups, re-read each call.
     *
     * <p>Not cached: the config is reloadable, a break is already doing far more work than a handful
     * of tag lookups, and a stale cache here would mean an anchor quietly using the old rules until
     * the server restarted.
     */
    private static List<TagKey<Block>> configuredGroups() {
        return parse(ChronoclonesConfig.COHERENCE_GROUPS.get());
    }

    /** The shipped defaults, for tests and for anything that cannot reach a loaded config. */
    public static List<TagKey<Block>> defaultGroups() {
        return parse(ChronoclonesConfig.DEFAULT_COHERENCE_GROUPS);
    }

    private static List<TagKey<Block>> parse(List<? extends String> ids) {
        List<TagKey<Block>> keys = new ArrayList<>();
        for (String id : ids) {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) {
                Chronoclones.LOGGER.warn("Ignoring malformed coherence group tag id '{}'", id);
                continue;
            }
            keys.add(TagKey.create(Registries.BLOCK, parsed));
        }
        return keys;
    }
}
