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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantments;
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
        ChronoclonesGameTests.add("break_needs_the_tool_in_the_anchor",
                BreakingGameTest::breakNeedsTheToolInTheAnchor);
        ChronoclonesGameTests.add("break_digs_with_the_anchors_own_tool",
                BreakingGameTest::breakDigsWithTheAnchorsOwnTool);
        ChronoclonesGameTests.add("smart_tool_picks_something_the_recording_never_held",
                BreakingGameTest::smartToolPicksSomethingElse);
        ChronoclonesGameTests.add("smart_tool_falls_back_to_bare_hands",
                BreakingGameTest::smartToolFallsBackToHands);
        ChronoclonesGameTests.add("smart_tool_refuses_to_break_for_nothing",
                BreakingGameTest::smartToolRefusesToBreakForNothing);
    }

    /**
     * Told to pick for itself, the anchor uses a tool the recording never mentioned.
     *
     * <p>Recorded with a shovel, which is the wrong thing for stone and which the anchor does not
     * have either. Exact would report having no shovel; smart reaches for the pickaxe.
     */
    private static void smartToolPicksSomethingElse(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                smartly(breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_SHOVEL))));
        emptyEveryClone(anchor);
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.DIAMOND_PICKAXE), 1);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockNotPresent(Blocks.STONE, target);
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.COBBLESTONE) == 0) {
                        helper.fail("the stone went but dropped nothing, so it was not mined "
                                + "with the pickaxe the anchor was holding");
                    }
                })
                .thenSucceed();
    }

    /** Nothing a block needs, and nothing it needs: hands will do. */
    private static void smartToolFallsBackToHands(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                smartly(breakWith(Blocks.DIRT, new ItemStack(Items.NETHERITE_PICKAXE))));
        // Not a tool anywhere, and dirt does not ask for one.
        emptyEveryClone(anchor);

        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertBlockNotPresent(Blocks.DIRT, target);
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.DIRT) == 0) {
                        helper.fail("the dirt went but never reached the anchor");
                    }
                })
                .thenSucceed();
    }

    /**
     * And where hands would leave nothing behind, it stops instead.
     *
     * <p>Bare hands do break stone, eventually, and drop nothing for it. An anchor that destroys
     * what it cannot keep is worse than one that says it has no pickaxe.
     */
    private static void smartToolRefusesToBreakForNothing(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                smartly(breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_PICKAXE))));
        emptyEveryClone(anchor);
        // A shovel breaks stone for nothing, so it is no better than the hands beside it.
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.DIAMOND_SHOVEL), 1);

        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertBlockPresent(Blocks.STONE, target);
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("expected NO_ITEM with nothing that would earn the drops, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /** The same routine, told to choose its own tool. */
    private static Recording smartly(Recording recording) {
        return recording.withSettings(0,
                ActionSettings.DEFAULT.withTool(ActionSettings.ToolRule.SMART));
    }

    /**
     * A clone swings a tool it owns, or it does not swing.
     *
     * <p>Breaking was the one action taking what it needed from the recording rather than from the
     * inventory, so an empty anchor mined with a netherite pickaxe it had never been given.
     */
    private static void breakNeedsTheToolInTheAnchor(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.OBSIDIAN, new ItemStack(Items.DIAMOND_PICKAXE)));
        // The fixture stocks what a recording digs with; this is a test about not having it.
        emptyEveryClone(anchor);

        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    helper.assertBlockPresent(Blocks.OBSIDIAN, target);
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("expected NO_ITEM digging with a tool the anchor lacks, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /**
     * And it swings the one it owns, not the one in the recording.
     *
     * <p>Read off the drops, which say which pickaxe was in the clone's hand without any waiting
     * about: recorded with Silk Touch and stocked with a plain pickaxe of the same kind, so stone
     * coming back whole would be the recording's enchantment still doing the work.
     */
    private static void breakDigsWithTheAnchorsOwnTool(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ItemStack silked = new ItemStack(Items.DIAMOND_PICKAXE);
        silked.enchant(helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, silked));
        emptyEveryClone(anchor);
        // The same kind of pickaxe, so the slot rule still finds one, and a plain one.
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.DIAMOND_PICKAXE), 1);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertBlockNotPresent(Blocks.STONE, target);
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.STONE) > 0) {
                        helper.fail("stone came back whole: the recording's Silk Touch was doing "
                                + "the digging, not the plain pickaxe the anchor holds");
                        return;
                    }
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.COBBLESTONE) == 0) {
                        helper.fail("the stone was broken but dropped nothing at all");
                    }
                })
                .thenSucceed();
    }

    /** Undoes the tool the fixture hands out, for the tests that are about not having one. */
    private static void emptyEveryClone(ChronoAnchorBlockEntity anchor) {
        for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
            var inventory = anchor.getCloneInventory(clone);
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.set(slot, ItemResource.EMPTY, 0);
            }
        }
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
