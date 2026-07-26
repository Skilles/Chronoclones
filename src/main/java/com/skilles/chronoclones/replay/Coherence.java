package com.skilles.chronoclones.replay;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How closely the world has to match the recording for an action to go ahead.
 *
 * <h2>Lenient by default; the lens buys precision</h2>
 *
 * <p>The tiers run the other way round from the spec's. A bare anchor is <em>lenient</em>: it breaks
 * whatever its tool can harvest. Fitting an Chrono Lens makes it <em>exact</em>: that block, or nothing.
 *
 * <p>Which way round matters more than it looks. The failure mode of a lenient default is a routine
 * that keeps working when the world moved slightly — the failure mode of a strict default is a
 * routine that silently does nothing and a player who concludes the mod is broken. Precision is the
 * thing worth asking for on purpose, because the only reason to want it is that you are relying on
 * exactly one block being exactly where you left it.
 *
 * <h2>Blocks only</h2>
 *
 * <p>This governs what a break accepts at its target square, and nothing else. How specific a
 * routine is about the <em>item transfers</em> it performs is {@link TransferPrecision}, which is set
 * per anchor rather than bought — because lenient block matching makes an anchor able to do more,
 * which is worth charging an upgrade slot for, whereas transfer precision only ever narrows what a
 * routine will touch and charging for a restriction is backwards.
 *
 * <h2>The tool decides, not a list</h2>
 *
 * <p>An earlier version matched blocks against configured groups of "similar" tags — stone stands in
 * for deepslate, oak for birch. That worked, but it was a table somebody had to maintain, it said
 * nothing about modded blocks nobody had thought to tag, and it answered the wrong question. The
 * player did not record "break a stone-like block"; they recorded swinging a particular tool at
 * whatever was in front of them.
 *
 * <p>So the rule is the game's own: a clone carries the tool the routine was recorded with, and it
 * can break whatever that tool can harvest. A diamond pickaxe reaches obsidian because a diamond
 * pickaxe reaches obsidian — no tag, no config, and a mod's new ore works on the day it ships
 * because its own {@code requiresCorrectToolForDrops} already says which picks are good enough.
 *
 * <p>Bare hands are the same rule with an empty stack, which is why they can clear dirt and leaves
 * and stop dead at stone.
 */
public final class Coherence {

    private Coherence() {}

    /** Anything the recorded tool can harvest. The default. */
    public static final int LENIENT = 0;
    /** That block. One Chrono Lens. */
    public static final int EXACT = 1;

    /** Whether this tier insists on the recorded block rather than an equivalent. */
    public static boolean isExact(int tier) {
        return tier >= EXACT;
    }

    /**
     * Whether the block that is actually there is one this routine may break.
     *
     * @param tool the stack the routine was recorded holding, empty for bare hands
     */
    public static boolean matches(BlockState candidate, Holder<Block> expected, int tier, ItemStack tool) {
        if (candidate.getBlock().equals(expected.value())) {
            return true;
        }
        if (isExact(tier)) {
            return false;
        }
        return canHarvest(candidate, tool);
    }

    /**
     * Whether {@code tool} is good enough for {@code state} to drop what it should.
     *
     * <p>Harvesting rather than merely breaking. A wooden pickaxe can spend a long time on iron ore
     * and destroy it for nothing, and a clone that did that would quietly consume a vein — so a
     * routine only substitutes into blocks it would actually get something out of.
     */
    public static boolean canHarvest(BlockState state, ItemStack tool) {
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }
}
