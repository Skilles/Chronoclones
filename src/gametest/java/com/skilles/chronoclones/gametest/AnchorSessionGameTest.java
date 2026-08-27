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

    private static void stocksAnotherAnchor(GameTestHelper helper) {
        ChronoAnchorBlockEntity target = AnchorTestFixture.placeAndImprint(
                helper, TARGET, idleRoutine());
        emptyOut(target);

        Recording recording = recordStocking(helper, target);
        if (recording == null) {
            return;
        }
        emptyOut(target);

        ChronoAnchorBlockEntity runner =
                AnchorTestFixture.placeAndImprint(helper, RUNNER, recording);
        runner.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND, 5));

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

    private static void saysSoWhenThePageIsGone(GameTestHelper helper) {
        ChronoAnchorBlockEntity target = AnchorTestFixture.placeAndImprint(
                helper, TARGET, idleRoutine());
        emptyOut(target);

        Recording recording = recordStocking(helper, target);
        if (recording == null) {
            return;
        }

        emptyOut(target);
        target.clearRecording();

        ChronoAnchorBlockEntity runner =
                AnchorTestFixture.placeAndImprint(helper, RUNNER, recording);
        runner.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND, 5));

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

    private static @org.jspecify.annotations.Nullable Recording recordStocking(
            GameTestHelper helper, ChronoAnchorBlockEntity target) {
        BlockPos absoluteRunner = helper.absolutePos(RUNNER);
        ServerPlayer player = AnchorTestFixture.mockServerPlayer(helper);
        player.snapTo(absoluteRunner.getX() + 0.5, absoluteRunner.getY(), absoluteRunner.getZ() + 0.5);
        player.setYRot(180.0f);

        RecordingSession session = RecordingSessions.start(player);
        try {
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));

            BlockPos absoluteTarget = helper.absolutePos(TARGET);
            ContainerWatch.noteInteraction(player, absoluteTarget, session);

            player.containerMenu = new ChronoAnchorMenu(
                    1, player.getInventory(), target, target.getContainerData());
            ContainerWatch.onContainerOpened(player, session);

            int hotbar = player.containerMenu.slots.size() - 9;
            click(player, hotbar);
            click(player, 0);

            ChronoAction.UseContainer recorded = ContainerWatch.onContainerClosed(player, session);
            if (recorded == null) {
                helper.fail("clicking inside an anchor recorded no session");
                return null;
            }
            session.record(recorded, Vec3.atCenterOf(absoluteTarget), 0);
            return session.finish();
        } finally {
            RecordingSessions.discard(player);
            ContainerWatch.forget(player);
        }
    }

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

    /** Nothing is told to the watch by hand: the mixin on clicked is what records a click. */
    private static void click(ServerPlayer player, int slot) {
        player.containerMenu.clicked(slot, 0, ContainerInput.PICKUP, player);
    }

    private static Recording idleRoutine() {
        return new Recording(
                List.of(new com.skilles.chronoclones.recording.MotionSample(
                        0, net.minecraft.world.phys.Vec3.ZERO, 0f, 0f)),
                List.of(),
                40, AnchorTestFixture.AUTHOR_NAME, AnchorTestFixture.AUTHOR_ID);
    }

    private static void emptyOut(ChronoAnchorBlockEntity anchor) {
        for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
            var inventory = anchor.getCloneInventory(clone);
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
