package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.UpgradeState;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What an Chrono Lens lets a routine break, and what it still refuses.
 *
 * <p>These live here rather than in JUnit because the answer runs through
 * {@code requiresCorrectToolForDrops} and the tool's harvest tier, both datapack-driven — outside a
 * running server every tag is empty and an assertion about a diamond pickaxe reaching obsidian would
 * pass whether or not the feature exists.
 *
 * <p>Both directions are asserted on purpose. A lens that accepts more is only safe if it still
 * refuses what the recorded tool could not have harvested, which is the whole substance of the rule.
 */
final class CoherenceGameTest {

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private CoherenceGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("coherence_strict_refuses_another_block",
                CoherenceGameTest::strictRefusesAnother);
        // Obsidian with a diamond pickaxe is genuinely ~190 ticks of mining, so this one needs a
        // window that fits the thing it is asserting.
        ChronoclonesGameTests.add("break_takes_time_in_survival", 400, CoherenceGameTest::survivalBreakTakesTime);
        ChronoclonesGameTests.add("coherence_lens_reaches_what_the_tool_reaches", 400,
                CoherenceGameTest::lensFollowsTheTool);
        ChronoclonesGameTests.add("coherence_lens_refuses_what_the_tool_cannot_harvest",
                CoherenceGameTest::lensRefusesTooHard);
        ChronoclonesGameTests.add("coherence_bare_hands_clear_soft_blocks",
                CoherenceGameTest::bareHandsClearSoftBlocks);
        ChronoclonesGameTests.add("break_is_instant_from_a_creative_recording",
                CoherenceGameTest::creativeBreakIsInstant);
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

    /** A routine recorded breaking stone, with whatever tool. */
    private static Recording breakWith(Block expected, ItemStack tool) {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, new ChronoAction.BreakBlock(
                        new BlockPos(0, 0, -1),
                        BuiltInRegistries.BLOCK.wrapAsHolder(expected),
                        tool))),
                20, AnchorTestFixture.AUTHOR_NAME, AnchorTestFixture.AUTHOR_ID);
    }

    /** A bare anchor. Anything but the recorded block is left alone. */
    private static void strictRefusesAnother(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DEEPSLATE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_PICKAXE)));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertBlockPresent(Blocks.DEEPSLATE, target);
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("expected WRONG_BLOCK without a lens, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /**
     * The rule in one assertion: a diamond pickaxe reaches obsidian, so a routine recorded with one
     * reaches obsidian — no tag list, no config, just what the tool can do.
     */
    private static void lensFollowsTheTool(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_PICKAXE)));
        fitLens(anchor);

        // Obsidian with a diamond pickaxe is over nine seconds of mining, which is the point.
        helper.startSequence()
                .thenExecuteAfter(250, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    /** A wooden pickaxe never harvested obsidian, so a routine holding one does not either. */
    private static void lensRefusesTooHard(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OBSIDIAN);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, new ItemStack(Items.WOODEN_PICKAXE)));
        fitLens(anchor);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertBlockPresent(Blocks.OBSIDIAN, target);
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("a lens must not let a wooden pickaxe reach obsidian — got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /**
     * Hands are a tool like any other.
     *
     * <p>A player can clear dirt bare-handed and cannot touch stone that way, and the same rule
     * produces both without either being spelled out anywhere.
     */
    private static void bareHandsClearSoftBlocks(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.GRASS_BLOCK, ItemStack.EMPTY));
        fitLens(anchor);

        helper.startSequence()
                .thenExecuteAfter(60, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    private static void fitLens(ChronoAnchorBlockEntity anchor) {
        anchor.getUpgradeHandler().set(1, ItemResource.of(ModItems.CHRONO_LENS.get()),
                UpgradeState.MAX_COHERENCE);
    }
}
