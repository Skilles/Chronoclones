package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.UpgradeState;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What an Chrono Lens changes about what a routine will break.
 *
 * <p>These live here rather than in JUnit because the answer depends on block tags, and tags come
 * from datapacks — outside a running server every tag is empty and an assertion about deepslate
 * standing in for stone would pass whether or not the feature exists. {@code CoherenceTest} covers
 * the parts of the rule that hold without tags.
 *
 * <p>Both directions are asserted on purpose. A lens that accepts more is only safe if it still
 * refuses the things a mining routine has no business touching, and "accepts everything" is exactly
 * what the naive reading of the spec would have produced.
 */
final class CoherenceGameTest {

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private CoherenceGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("coherence_strict_refuses_a_related_block",
                CoherenceGameTest::strictRefusesRelated);
        ChronoclonesGameTests.add("coherence_lens_accepts_a_related_block",
                CoherenceGameTest::lensAcceptsRelated);
        ChronoclonesGameTests.add("coherence_lens_still_refuses_an_unrelated_block",
                CoherenceGameTest::lensRefusesUnrelated);
    }

    /** A bare anchor. Deepslate where stone was recorded is left alone — the shipped behaviour. */
    private static void strictRefusesRelated(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DEEPSLATE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertBlockPresent(Blocks.DEEPSLATE, target);
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("expected WRONG_BLOCK without a lens, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    /** One lens. The quarry that reached the deepslate layer keeps going. */
    private static void lensAcceptsRelated(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DEEPSLATE);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));
        fitLens(anchor);

        helper.startSequence()
                .thenExecuteAfter(15, () -> helper.assertBlockPresent(Blocks.AIR, target))
                .thenSucceed();
    }

    /**
     * The refusal that makes the rest safe.
     *
     * <p>An oak log is in a configured group, and so is stone — but not the same one. If groups ever
     * flatten into each other, this is the test that notices, and the symptom in a real world would
     * be a stone-clearing routine eating the timber of whatever it was pointed at.
     */
    private static void lensRefusesUnrelated(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.OAK_LOG);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));
        fitLens(anchor);

        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertBlockPresent(Blocks.OAK_LOG, target);
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.WRONG_BLOCK) {
                        helper.fail("a lens must not make a routine accept anything at all — got "
                                + anchor.getLastFailure().reason() + " for oak where stone was recorded");
                    }
                })
                .thenSucceed();
    }

    private static void fitLens(ChronoAnchorBlockEntity anchor) {
        anchor.getUpgradeHandler().set(1, ItemResource.of(ModItems.CHRONO_LENS.get()),
                UpgradeState.MAX_COHERENCE);
    }
}
