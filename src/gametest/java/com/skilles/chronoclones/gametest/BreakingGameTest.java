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

final class BreakingGameTest {

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private BreakingGameTest() {}

    static void register() {
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

    private static void smartToolPicksSomethingElse(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                smartly(breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_SHOVEL))));
        emptyEveryClone(anchor);
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND_PICKAXE, 1));

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

    private static void smartToolFallsBackToHands(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                smartly(breakWith(Blocks.DIRT, new ItemStack(Items.NETHERITE_PICKAXE))));
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

    private static void smartToolRefusesToBreakForNothing(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                smartly(breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_PICKAXE))));
        emptyEveryClone(anchor);
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND_SHOVEL, 1));

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

    private static Recording smartly(Recording recording) {
        return recording.withSettings(0,
                ActionSettings.DEFAULT.withTool(ActionSettings.ToolRule.SMART));
    }

    private static void breakNeedsTheToolInTheAnchor(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.OBSIDIAN, new ItemStack(Items.DIAMOND_PICKAXE)));
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

    private static void breakDigsWithTheAnchorsOwnTool(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ItemStack silked = new ItemStack(Items.DIAMOND_PICKAXE);
        silked.enchant(helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, silked));
        emptyEveryClone(anchor);
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND_PICKAXE, 1));

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

    private static void emptyEveryClone(ChronoAnchorBlockEntity anchor) {
        for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
            var inventory = anchor.getCloneInventory(clone);
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void widenedPlacementUsesWhatItHas(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.AIR);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.PlaceBlock(
                                new BlockPos(0, 0, -1), Direction.UP,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE),
                                Blocks.STONE.defaultBlockState()),
                        ActionSettings.DEFAULT.withRecordedSubject(false)));

        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.DIRT, 8));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertBlockPresent(Blocks.DIRT, target);
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.DIRT) != 7) {
                        helper.fail("the dirt it placed was never paid for");
                    }
                })
                .thenSucceed();
    }

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

    private static void creativeBreakIsInstant(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        Recording survivalShape = breakWith(Blocks.OBSIDIAN, new ItemStack(Items.DIAMOND_PICKAXE));
        AnchorTestFixture.placeAndImprint(helper, ANCHOR, new Recording(
                survivalShape.motion(), survivalShape.actions(), survivalShape.lengthTicks(),
                survivalShape.authorName(), survivalShape.authorId(), true));

        helper.startSequence()
                .thenExecuteAfter(15, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    private static void breaksWhateverIsThere(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_PICKAXE))
                        .withSettings(0, ActionSettings.DEFAULT.withRecordedSubject(false)));

        helper.startSequence()
                .thenExecuteAfter(250, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    private static void aPoorToolIsSlowNotRefused(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

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

    private static void bareHandsClearSoftBlocks(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.DIRT, ItemStack.EMPTY));

        helper.startSequence()
                .thenExecuteAfter(60, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

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
