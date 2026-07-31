package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.ContainerWatch;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingSession;
import com.skilles.chronoclones.recording.RecordingSessions;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Routines that stock another anchor.
 *
 * <p>An anchor's own menu used to be excluded from recording outright, so a player filling one
 * while a recorder ran captured nothing and the routine they were building quietly had a hole in
 * it. Chaining anchors -- one routine feeding the storage another works out of -- is the obvious
 * thing to want and could not be recorded at all.
 */
final class AnchorSessionGameTest {

    private AnchorSessionGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("anchor_session_stocks_another_anchor",
                AnchorSessionGameTest::stocksAnotherAnchor);
        ChronoclonesGameTests.add("anchor_session_says_so_when_the_page_is_gone",
                AnchorSessionGameTest::saysSoWhenThePageIsGone);
    }

    private static final BlockPos RUNNER = new BlockPos(8, 1, 8);
    private static final BlockPos TARGET = new BlockPos(8, 1, 5);

    /** What a player put into an anchor, a clone puts into it too. */
    private static void stocksAnotherAnchor(GameTestHelper helper) {
        ChronoAnchorBlockEntity target = AnchorTestFixture.placeAndImprint(
                helper, TARGET, idleRoutine());
        emptyOut(target);

        Recording recording = recordStocking(helper, target);
        if (recording == null) {
            return;
        }
        // Recording it put real diamonds in there. Take them out, or this measures the player.
        emptyOut(target);

        ChronoAnchorBlockEntity runner =
                AnchorTestFixture.placeAndImprint(helper, RUNNER, recording);
        runner.getCloneInventory(0).set(0, ItemResource.of(new ItemStack(Items.DIAMOND)), 5);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    int landed = AnchorTestFixture.countIn(target.getInventory(), Items.DIAMOND);
                    if (landed == 0) {
                        helper.fail("the routine never put anything into the other anchor, "
                                + "reporting " + runner.getLastFailure().reason()
                                + "; it recorded " + describe(recording));
                    }
                })
                .thenSucceed();
    }

    /**
     * A page that is not there is said so, rather than the items going somewhere else.
     *
     * <p>A blank anchor's squares are unreachable for the same reason a missing clone's are: the
     * storage belongs to the routine that fills it, and one with nothing to run has nowhere to put
     * anything.
     */
    private static void saysSoWhenThePageIsGone(GameTestHelper helper) {
        ChronoAnchorBlockEntity target = AnchorTestFixture.placeAndImprint(
                helper, TARGET, idleRoutine());
        emptyOut(target);

        Recording recording = recordStocking(helper, target);
        if (recording == null) {
            return;
        }

        emptyOut(target);
        // Take the target's routine away, which takes its storage with it.
        target.clearRecording();

        ChronoAnchorBlockEntity runner =
                AnchorTestFixture.placeAndImprint(helper, RUNNER, recording);
        runner.getCloneInventory(0).set(0, ItemResource.of(new ItemStack(Items.DIAMOND)), 5);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    DiagnosticState.FailureReason reason = runner.getLastFailure().reason();
                    if (reason != DiagnosticState.FailureReason.NO_SLOT) {
                        helper.fail("expected the routine to report a missing square, got " + reason
                                + "; it recorded " + describe(recording));
                        return;
                    }
                    if (AnchorTestFixture.countIn(target.getInventory(), Items.DIAMOND) != 0) {
                        helper.fail("items went into an anchor that had nowhere to put them");
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------- fixtures

    /**
     * Records a player moving a diamond out of their own inventory into {@code target}'s storage.
     */
    private static @org.jspecify.annotations.Nullable Recording recordStocking(
            GameTestHelper helper, ChronoAnchorBlockEntity target) {

        BlockPos absoluteRunner = helper.absolutePos(RUNNER);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Standing where the routine will run, so its local coordinates land back on the target.
        player.snapTo(absoluteRunner.getX() + 0.5, absoluteRunner.getY(), absoluteRunner.getZ() + 0.5);
        player.setYRot(180.0f);

        RecordingSession session = RecordingSessions.start(player);
        try {
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));

            BlockPos absoluteTarget = helper.absolutePos(TARGET);
            ContainerWatch.noteInteraction(player, absoluteTarget, session);

            // Built rather than opened through the block: opening an anchor sends its timeline as
            // extra screen data, and a mock player has no connection that will take it. The watch
            // reads whatever menu the player has, which is the part being tested.
            player.containerMenu = new ChronoAnchorMenu(
                    1, player.getInventory(), target, target.getContainerData());
            ContainerWatch.onContainerOpened(player, session);

            // The player's hotbar slot, into the first clone's first square.
            int hotbar = player.containerMenu.slots.size() - 9;
            click(player, hotbar);
            click(player, 0);

            ChronoAction.UseContainer recorded = ContainerWatch.onContainerClosed(player, session);
            if (recorded == null) {
                helper.fail("clicking inside an anchor recorded no session");
                return null;
            }
            // What RecordingCapture does when the close event fires: the watch hands back the
            // session, and the session is what goes into the recording.
            session.record(recorded, Vec3.atCenterOf(absoluteTarget), 0);
            return session.finish();
        } finally {
            RecordingSessions.discard(player);
            ContainerWatch.forget(player);
        }
    }

    /** What a routine turned out to contain, for a failure message worth reading. */
    private static String describe(Recording recording) {
        StringBuilder out = new StringBuilder();
        for (var timed : recording.actions()) {
            if (timed.action() instanceof ChronoAction.UseContainer session) {
                out.append("a session of ").append(session.steps().size()).append(" step(s)");
                for (var step : session.steps()) {
                    out.append(" | ").append(step);
                }
            } else {
                out.append(timed.action().type());
            }
        }
        return out.toString();
    }

    /**
     * One click, made for real.
     *
     * <p>Nothing is told to the watch by hand: the mixin on {@code clicked} is what records a click
     * in the game, and calling the watch as well recorded every click twice, which left the
     * interpreter a run of unpairable clicks instead of one move.
     */
    private static void click(ServerPlayer player, int slot) {
        player.containerMenu.clicked(slot, 0, ContainerInput.PICKUP, player);
    }

    /** A routine that does nothing, which is enough to give an anchor usable storage. */
    private static Recording idleRoutine() {
        return new Recording(
                List.of(new com.skilles.chronoclones.recording.MotionSample(
                        0, net.minecraft.world.phys.Vec3.ZERO, 0f, 0f)),
                List.of(),
                40, AnchorTestFixture.AUTHOR_NAME, AnchorTestFixture.AUTHOR_ID);
    }

    /** The fixture stocks recorded tools; this test is about what the routine moves. */
    private static void emptyOut(ChronoAnchorBlockEntity anchor) {
        for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
            var inventory = anchor.getCloneInventory(clone);
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.set(slot, ItemResource.EMPTY, 0);
            }
        }
    }
}
