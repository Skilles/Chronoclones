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
 * What a routine will break, how long it takes, and what an Chrono Lens stops it breaking.
 *
 * <p>These live here rather than in JUnit because mining is datapack-driven from end to end —
 * hardness, harvest tiers, {@code requiresCorrectToolForDrops} — and outside a running server every
 * one of those queries answers zero, so an assertion about a pickaxe and a block would pass whether
 * or not the feature existed.
 *
 * <p>The pair that matters most is a good tool and a bad one against the same block. An anchor that
 * swings whatever it recorded at whatever is there has to be slow where a player would be slow,
 * because "it eventually gives up" and "it takes nine seconds" look identical for the first second
 * and are completely different features.
 */
final class CoherenceGameTest {

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private CoherenceGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("coherence_lens_refuses_another_block",
                CoherenceGameTest::lensRefusesAnother);
        // Obsidian with a diamond pickaxe is genuinely ~190 ticks of mining, so this one needs a
        // window that fits the thing it is asserting.
        ChronoclonesGameTests.add("break_takes_time_in_survival", 400, CoherenceGameTest::survivalBreakTakesTime);
        ChronoclonesGameTests.add("coherence_breaks_whatever_is_there", 400,
                CoherenceGameTest::breaksWhateverIsThere);
        ChronoclonesGameTests.add("coherence_a_poor_tool_is_slow_not_refused",
                CoherenceGameTest::aPoorToolIsSlowNotRefused);
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

    /** With a lens fitted, anything but the recorded block is left alone. */
    private static void lensRefusesAnother(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DEEPSLATE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.STONE, new ItemStack(Items.DIAMOND_PICKAXE)));
        fitLens(anchor);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertBlockPresent(Blocks.DEEPSLATE, target);
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("expected WRONG_BLOCK with a lens fitted, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /**
     * The rule in one assertion: the routine recorded stone, obsidian is there, and the clone swings
     * its pickaxe at the obsidian.
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
     * WRONG_BLOCK here, which was the mod second-guessing what the player meant — but the game's own
     * arithmetic makes it hopeless: a tool that cannot harvest a block mines it at a hundredth
     * speed, so a clone with the wrong pickaxe stands there achieving nothing, exactly as a player
     * with the wrong pickaxe does.
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

    /**
     * Hands are a tool like any other.
     *
     * <p>A player can clear dirt bare-handed and cannot touch stone that way, and the same rule
     * produces both without either being spelled out anywhere.
     */
    private static void bareHandsClearSoftBlocks(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);

        AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                breakWith(Blocks.GRASS_BLOCK, ItemStack.EMPTY));

        helper.startSequence()
                .thenExecuteAfter(60, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    private static void fitLens(ChronoAnchorBlockEntity anchor) {
        anchor.getUpgradeHandler().set(1, ItemResource.of(ModItems.CHRONO_LENS.get()),
                UpgradeState.MAX_COHERENCE);
    }
}
