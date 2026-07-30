package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What a routine breaks, and how long it takes about it.
 */
final class BreakingGameTest {

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private BreakingGameTest() {}

    static void register() {
        // Obsidian with a diamond pickaxe is ~190 ticks, so these need a longer window.
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
        ChronoclonesGameTests.add("place_widened_to_any_block_builds_with_what_it_has",
                BreakingGameTest::widenedPlacementUsesWhatItHas);
    }

    /**
     * A placement told it no longer cares which block builds with whatever the clone was given.
     *
     * <p>The other side of {@code break_refuses_a_block_it_was_not_recorded_on}: the same option,
     * on the action that puts blocks down rather than the one that takes them up.
     */
    private static void widenedPlacementUsesWhatItHas(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.AIR);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.PlaceBlock(
                                new BlockPos(0, 0, -1), Direction.UP,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE),
                                Blocks.STONE.defaultBlockState()),
                        ActionSettings.DEFAULT.withRecordedSubject(false)));

        // Not one stone anywhere: only the widened rule can find anything to build with.
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.DIRT), 8);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertBlockPresent(Blocks.DIRT, target);
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.DIRT) != 7) {
                        helper.fail("the dirt it placed was never paid for");
                    }
                })
                .thenSucceed();
    }

    /**
     * A survival routine mines rather than deletes.
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
     * Widened to any block, the routine recorded stone swings its pickaxe at the obsidian there now.
     */
    private static void breaksWhateverIsThere(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_PICKAXE))
                        .withSettings(0, ActionSettings.DEFAULT.withRecordedSubject(false)));

        // Obsidian with a diamond pickaxe is over nine seconds of mining, which is the point.
        helper.startSequence()
                .thenExecuteAfter(250, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    /**
     * A wooden pickaxe on obsidian gets nowhere, and gets nowhere the way a player would.
     */
    private static void aPoorToolIsSlowNotRefused(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        // Recorded on the obsidian it is aimed at, so the tool is the only thing under test.
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.OBSIDIAN, new ItemStack(Items.WOODEN_PICKAXE)));

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
                breakWith(Blocks.DIRT, ItemStack.EMPTY));

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
