package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * What a routine breaks, and how long it takes about it.
 *
 * <p>A clone swings the tool it was recorded with at the square it was recorded at, and nothing
 * inspects the block first. Everything interesting about that follows from the game's own mining
 * arithmetic, which is why these live here rather than in JUnit: hardness, harvest tiers and
 * {@code requiresCorrectToolForDrops} are all datapack-driven, and outside a running server every
 * one of those queries answers zero.
 *
 * <p>The pair that matters most is a good tool and a bad one against the same block. "It gives up"
 * and "it takes nine seconds" look identical for the first second and are completely different
 * features, so both directions are pinned.
 */
final class BreakingGameTest {

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private BreakingGameTest() {}

    static void register() {
        // Obsidian with a diamond pickaxe is genuinely ~190 ticks of mining, so the tests that wait
        // for one need a window that fits the thing they are asserting.
        ChronoclonesGameTests.add("break_takes_time_in_survival", 400,
                BreakingGameTest::survivalBreakTakesTime);
        ChronoclonesGameTests.add("break_is_instant_from_a_creative_recording",
                BreakingGameTest::creativeBreakIsInstant);
        ChronoclonesGameTests.add("break_whatever_is_in_the_square", 400,
                BreakingGameTest::breaksWhateverIsThere);
        ChronoclonesGameTests.add("break_with_a_poor_tool_is_slow_not_refused",
                BreakingGameTest::aPoorToolIsSlowNotRefused);
        ChronoclonesGameTests.add("break_bare_hands_clear_soft_blocks",
                BreakingGameTest::bareHandsClearSoftBlocks);
    }

    /**
     * A survival routine mines rather than deletes.
     *
     * <p>Asserted as "still there early, gone later" rather than by counting ticks, because the exact
     * duration is the game's own arithmetic and pinning it here would make this a test of vanilla.
     * What matters is that there is a middle — before this, a break was one indivisible instant.
     */
    private static void survivalBreakTakesTime(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.OBSIDIAN, new ItemStack(Items.DIAMOND_PICKAXE)));

        helper.startSequence()
                .thenExecuteAfter(20, () -> helper.assertBlockPresent(Blocks.OBSIDIAN, target))
                .thenExecuteAfter(230, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    /**
     * A creative recording keeps creative's instant break.
     *
     * <p>The flag is on the recording, not on the anchor, so a routine built in creative stays
     * instant after it is handed to somebody playing survival — it describes what the author did.
     */
    private static void creativeBreakIsInstant(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        Recording survivalShape = breakWith(Blocks.OBSIDIAN, new ItemStack(Items.DIAMOND_PICKAXE));
        AnchorTestFixture.placeAndImprint(helper, ANCHOR, new Recording(
                survivalShape.motion(), survivalShape.actions(), survivalShape.lengthTicks(),
                survivalShape.authorName(), survivalShape.authorId(), true));

        // Well inside the ~190 ticks the same block takes in survival.
        helper.startSequence()
                .thenExecuteAfter(15, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    /**
     * The routine recorded stone, obsidian is there, and the clone swings its pickaxe at the obsidian.
     *
     * <p>No tag list, no config, and no judgement about whether the block is a reasonable substitute
     * for the recorded one. A player who walked up with a diamond pickaxe would have mined it.
     */
    private static void breaksWhateverIsThere(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_PICKAXE)));

        // Obsidian with a diamond pickaxe is over nine seconds of mining, which is the point.
        helper.startSequence()
                .thenExecuteAfter(250, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    /**
     * A wooden pickaxe on obsidian gets nowhere, and gets nowhere the way a player would.
     *
     * <p>The counterpart to the test above, and the reason "break whatever is there" is not the
     * free-for-all it sounds like. Nothing refuses the attempt — an earlier version reported
     * WRONG_BLOCK here, which was the mod second-guessing what the player meant — but a tool that
     * cannot harvest a block mines it at a hundredth speed, so a clone with the wrong pickaxe stands
     * there achieving nothing, exactly as a player with the wrong pickaxe does.
     */
    private static void aPoorToolIsSlowNotRefused(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, new ItemStack(Items.WOODEN_PICKAXE)));

        helper.startSequence()
                .thenExecuteAfter(120, () -> {
                    helper.assertBlockPresent(Blocks.OBSIDIAN, target);
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NONE) {
                        helper.fail("the attempt was refused rather than merely slow: "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /** Hands are a tool like any other: a routine recorded empty-handed still clears dirt. */
    private static void bareHandsClearSoftBlocks(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.GRASS_BLOCK, ItemStack.EMPTY));

        helper.startSequence()
                .thenExecuteAfter(60, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    /** A routine recorded breaking one block, with whatever tool. */
    private static Recording breakWith(Block expected, ItemStack tool) {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, new ChronoAction.BreakBlock(
                        new BlockPos(0, 0, -1),
                        BuiltInRegistries.BLOCK.wrapAsHolder(expected),
                        tool))),
                20, AnchorTestFixture.AUTHOR_NAME, AnchorTestFixture.AUTHOR_ID);
    }
}
