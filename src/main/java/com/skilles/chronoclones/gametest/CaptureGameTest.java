package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.RecordingSession;
import com.skilles.chronoclones.recording.RecordingSessions;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Capture behaviour that can only be checked against a running world.
 *
 * <p>Specifically the feedback loop: anchors act through a fake player wearing the <em>owner's</em>
 * UUID, so while that owner records, their own anchors' breaks arrive at the capture handlers under
 * the identity being recorded. Left alone, a routine absorbs its own clones and every re-record
 * compounds the previous one's output.
 */
final class CaptureGameTest {

    private CaptureGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("recording_ignores_own_clones", CaptureGameTest::recordingIgnoresOwnClones);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private static void recordingIgnoresOwnClones(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ServerLevel level = helper.getLevel();
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        // Park the actor on the anchor before opening the session. The session origin is snapshotted
        // at start, and out-of-range actions are dropped anyway — so a session started from wherever
        // the fake player happened to be would pass this test for the wrong reason.
        FakePlayer owner = AnchorTestFixture.owner(level);
        BlockPos anchorAbsolute = helper.absolutePos(ANCHOR);
        owner.setPos(anchorAbsolute.getX() + 0.5, anchorAbsolute.getY(), anchorAbsolute.getZ() + 0.5);

        // Stands in for the owner recording a second routine while their first anchor runs. Real
        // players cannot be spawned here, but the collision under test is one of UUIDs, and this
        // session is keyed by exactly the UUID the anchor will act under.
        RecordingSession session = RecordingSessions.start(owner);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    try {
                        // Fails loudly rather than passing vacuously if replay never happened.
                        helper.assertBlockNotPresent(Blocks.STONE, target);

                        if (session.actionCount() != 0) {
                            helper.fail("the anchor's own replay was captured into the recording ("
                                    + session.actionCount() + " action(s)) - a routine must never "
                                    + "record its own clones");
                        }
                        if (anchor.getRecording() == null) {
                            helper.fail("the anchor lost its routine mid-test");
                        }
                    } finally {
                        // Static state shared with every other test in this run; discard() will not
                        // remove it, since it now refuses fake players by design.
                        RecordingSessions.clear();
                    }
                })
                .thenSucceed();
    }
}
