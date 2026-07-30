package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.ContainerWatch;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.RecordingSession;
import com.skilles.chronoclones.recording.RecordingSessions;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Capture behaviour that can only be checked against a running world.
 */
final class CaptureGameTest {

    private CaptureGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("recording_ignores_own_clones", CaptureGameTest::recordingIgnoresOwnClones);
        ChronoclonesGameTests.add("a_recorded_session_remembers_what_it_opened",
                CaptureGameTest::recordedSessionRemembersWhatItOpened);
    }

    /**
     * A session keeps the block it was opened on, which is what the editor draws it as.
     */
    private static void recordedSessionRemembersWhatItOpened(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.CHEST);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RecordingSession session = RecordingSessions.start(player);
        try {
            BlockPos absolute = helper.absolutePos(target);
            ContainerWatch.noteInteraction(player, absolute, -1, session);

            // Opening the chest for real, so the watch sees the menu the player is looking at.
            helper.useBlock(target, player);
            if (!(player.containerMenu instanceof ChestMenu)) {
                helper.fail("the chest never opened, so nothing was recorded to check");
                return;
            }
            ContainerWatch.onContainerOpened(player, session);
            ContainerWatch.onClick(player, 0, 0, ContainerInput.PICKUP);

            ChronoAction.UseContainer recorded = ContainerWatch.onContainerClosed(player, session);
            if (recorded == null) {
                helper.fail("nothing was recorded for a session that was clicked in");
                return;
            }
            if (!(recorded.target() instanceof MenuTarget.Block block)) {
                helper.fail("a chest was recorded as " + recorded.target());
                return;
            }
            if (block.expectedBlock().map(Holder::value).orElse(null) != Blocks.CHEST) {
                helper.fail("the session did not remember the chest it was opened on, got "
                        + block.expectedBlock().map(Holder::value).orElse(null));
            }
        } finally {
            RecordingSessions.discard(player);
            ContainerWatch.forget(player);
        }
        helper.succeed();
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    private static void recordingIgnoresOwnClones(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ServerLevel level = helper.getLevel();
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        // The session origin is snapshotted at start, so park the actor first.
        FakePlayer owner = AnchorTestFixture.owner(level);
        BlockPos anchorAbsolute = helper.absolutePos(ANCHOR);
        owner.setPos(anchorAbsolute.getX() + 0.5, anchorAbsolute.getY(), anchorAbsolute.getZ() + 0.5);

        // The collision under test is one of UUIDs, and this session is keyed by the one
        // the anchor will act under.
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
                        // discard() refuses fake players, so clear the shared state directly.
                        RecordingSessions.clear();
                    }
                })
                .thenSucceed();
    }
}
