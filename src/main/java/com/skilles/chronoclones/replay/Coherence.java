package com.skilles.chronoclones.replay;

import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether a break goes ahead when the block is not the one that was recorded.
 *
 * <h2>By default it goes ahead</h2>
 *
 * <p>A clone swings the tool the routine was recorded with at the square the routine named, and
 * whatever is there gets what a player would have given it. A wooden pickaxe on obsidian spends a
 * long time achieving nothing, exactly as it does in a player's hands — and that is the point. The
 * mod is not deciding what is worth hitting; it is repeating what somebody did.
 *
 * <p>Two earlier versions filtered instead, and both were wrong in the same direction. One matched
 * configured groups of "similar" tags, which was a table somebody had to maintain and said nothing
 * about modded blocks. The other asked whether the recorded tool could <em>harvest</em> what it
 * found, which sounds careful and is really a second guess at intent: the player did not think
 * "break anything a stone pickaxe drops", they swung a stone pickaxe at a square.
 *
 * <p>The cost is real and worth stating: an anchor whose routine mined stone will mine whatever ends
 * up in that square, including a wall somebody built there. Everything that bounds that is still in
 * place — the radius, the owner's identity on every break, the unbreakable tag, and a preview that
 * shows exactly which squares a routine touches before it is imprinted.
 *
 * <h2>The Chrono Lens buys the filter back</h2>
 *
 * <p>Which is the shape this should always have had. Refusing anything but the recorded block is a
 * restriction, and a restriction is worth having when you are relying on one block being exactly
 * where you left it — so it is a thing to go and craft rather than the default that surprises
 * everybody who has not.
 */
public final class Coherence {

    private Coherence() {}

    /** Break whatever is there. The default. */
    public static final int LENIENT = 0;
    /** Only the recorded block. One Chrono Lens. */
    public static final int EXACT = 1;

    /** Whether this tier insists on the recorded block. */
    public static boolean isExact(int tier) {
        return tier >= EXACT;
    }

    /** Whether the block that is actually there is one this routine may break. */
    public static boolean matches(BlockState candidate, Holder<Block> expected, int tier) {
        return !isExact(tier) || candidate.getBlock().equals(expected.value());
    }
}
