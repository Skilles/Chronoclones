package com.skilles.chronoclones.gametest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.Recording;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class AttributionGameTest {

    private AttributionGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("attribution_resolves_to_owner", AttributionGameTest::attributionResolvesToOwner);
        ChronoclonesGameTests.add("author_is_never_the_actor", AttributionGameTest::authorIsNeverTheActor);
        ChronoclonesGameTests.add("protection_can_cancel", AttributionGameTest::protectionCanCancel);
        ChronoclonesGameTests.add("author_survives_imprint", AttributionGameTest::authorSurvivesImprint);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static void attributionResolvesToOwner(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        AtomicReference<UUID> observed = new AtomicReference<>();
        BreakWatch watch = BreakWatch.at(helper.absolutePos(target),
                attempt -> observed.compareAndSet(null, attempt.playerId()));

        AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    watch.close();

                    UUID actor = observed.get();
                    if (actor == null) {
                        helper.fail("no block break event fired: the routine never executed");
                    }
                    if (!AnchorTestFixture.OWNER_ID.equals(actor)) {
                        helper.fail("break was attributed to " + actor + " but must be the anchor owner "
                                + AnchorTestFixture.OWNER_ID);
                    }
                })
                .thenSucceed();
    }

    private static void authorIsNeverTheActor(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        AtomicInteger authorAttributedBreaks = new AtomicInteger();
        BreakWatch watch = BreakWatch.at(helper.absolutePos(target), attempt -> {
            if (AnchorTestFixture.AUTHOR_ID.equals(attempt.playerId())) {
                authorAttributedBreaks.incrementAndGet();
            }
        });

        AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    watch.close();
                    if (authorAttributedBreaks.get() > 0) {
                        helper.fail("the recording author was used as the actor "
                                + authorAttributedBreaks.get() + " time(s): the griefing vector");
                    }
                })
                .thenSucceed();
    }

    private static void protectionCanCancel(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        BreakWatch watch = BreakWatch.at(helper.absolutePos(target), BreakWatch.Attempt::cancel);

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    watch.close();

                    helper.assertBlockPresent(Blocks.STONE, target);

                    DiagnosticState failure = anchor.getLastFailure();
                    if (failure.reason() != DiagnosticState.FailureReason.PROTECTED) {
                        helper.fail("expected a PROTECTED diagnostic after a cancelled break, got "
                                + failure.reason());
                    }
                })
                .thenSucceed();
    }

    private static void authorSurvivesImprint(GameTestHelper helper) {
        Recording recording = AnchorTestFixture.breakOneBlock(Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR, recording);

        Recording stored = anchor.getRecording();
        if (stored == null) {
            helper.fail("anchor did not keep the imprinted recording");
            return;
        }
        if (!AnchorTestFixture.AUTHOR_ID.equals(stored.authorId())) {
            helper.fail("author was rewritten on imprint: expected " + AnchorTestFixture.AUTHOR_ID
                    + " but got " + stored.authorId());
        }
        if (!AnchorTestFixture.OWNER_ID.equals(anchor.getOwnerId())) {
            helper.fail("anchor owner should be the imprinting player, got " + anchor.getOwnerId());
        }
        helper.succeed();
    }

}
