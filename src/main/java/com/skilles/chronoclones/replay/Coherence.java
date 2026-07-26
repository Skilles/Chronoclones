package com.skilles.chronoclones.replay;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How closely the world has to match the recording for a break to go ahead.
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

    /** Exact match only. What a bare anchor does. */
    public static final int STRICT = 0;
    /** Anything the recorded tool can harvest. One Chrono Lens. */
    public static final int LOOSE = 1;

    /**
     * Whether the block that is actually there is one this routine may break.
     *
     * @param tool the stack the routine was recorded holding, empty for bare hands
     */
    public static boolean matches(BlockState candidate, Holder<Block> expected, int tier, ItemStack tool) {
        if (candidate.getBlock().equals(expected.value())) {
            return true;
        }
        if (tier < LOOSE) {
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
