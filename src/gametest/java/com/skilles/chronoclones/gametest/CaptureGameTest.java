package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.ContainerWatch;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.RecordingSession;
import com.skilles.chronoclones.recording.RecordingSessions;
import com.skilles.chronoclones.recording.TimedAction;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        ChronoclonesGameTests.add("tilling_records_one_action_and_the_block_it_worked",
                CaptureGameTest::tillingRecordsOneAction);
        ChronoclonesGameTests.add("a_refused_interaction_records_nothing",
                CaptureGameTest::refusedInteractionRecordsNothing);
        ChronoclonesGameTests.add("a_refused_item_use_records_nothing",
                CaptureGameTest::refusedItemUseRecordsNothing);
        ChronoclonesGameTests.add("a_passing_main_hand_does_not_shadow_the_off_hand",
                CaptureGameTest::passingMainHandDoesNotShadowTheOffHand);
    }

    /**
     * A click the block ignored is not a thing the routine should spend forever repeating.
     *
     * <p>The interaction events fire before any of the work, so this used to record a stick being
     * used on stone as faithfully as it recorded a hoe tilling dirt.
     */
    private static void refusedInteractionRecordsNothing(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        BlockPos absolute = helper.absolutePos(target);
        ServerPlayer player = recordingPlayerAt(helper, absolute);
        RecordingSession session = RecordingSessions.start(player);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));

            List<TimedAction> recorded = session.finish().actions();
            if (!recorded.isEmpty()) {
                helper.fail("poking stone with a stick recorded " + recorded.size()
                        + " action(s): " + recorded.stream()
                                .map(a -> a.action().type().toString()).toList());
                return;
            }
            helper.succeed();
        } finally {
            RecordingSessions.discard(player);
        }
    }

    /**
     * Eating on a full stomach is a refusal, and a refusal is not an action.
     */
    private static void refusedItemUseRecordsNothing(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR));
        ServerPlayer player = recordingPlayerAt(helper, absolute);
        RecordingSession session = RecordingSessions.start(player);
        try {
            // A mock player is created invulnerable, and an invulnerable player can always eat
            // however full it is, so the refusal this test is about would never happen.
            player.setGameMode(GameType.SURVIVAL);
            player.getFoodData().setFoodLevel(20);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND);

            List<TimedAction> recorded = session.finish().actions();
            if (!recorded.isEmpty()) {
                helper.fail("eating on a full stomach recorded " + recorded.size() + " action(s)");
                return;
            }
            helper.succeed();
        } finally {
            RecordingSessions.discard(player);
        }
    }

    /**
     * Two hands are two calls, and only the one that did something is written down.
     *
     * <p>Vanilla tries the main hand and then the off hand. A main hand that passed used to record
     * a phantom action beside the real one, so the routine replayed a thing that had never happened.
     */
    private static void passingMainHandDoesNotShadowTheOffHand(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);
        helper.setBlock(target.above(), Blocks.AIR);

        BlockPos absolute = helper.absolutePos(target);
        ServerPlayer player = recordingPlayerAt(helper, absolute);
        RecordingSession session = RecordingSessions.start(player);
        try {
            BlockHitResult hit =
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);

            // The main hand has nothing dirt cares about; the off hand has a hoe.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_HOE));

            player.gameMode.useItemOn(player, helper.getLevel(),
                    player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
            player.gameMode.useItemOn(player, helper.getLevel(),
                    player.getItemInHand(InteractionHand.OFF_HAND), InteractionHand.OFF_HAND, hit);

            helper.assertBlockPresent(Blocks.FARMLAND, target);

            List<TimedAction> recorded = session.finish().actions();
            if (recorded.size() != 1) {
                helper.fail("a passing main hand and a working off hand recorded "
                        + recorded.size() + " actions, expected only the off hand's");
                return;
            }
            if (!(recorded.getFirst().action() instanceof ChronoAction.UseOnBlock use)
                    || use.hand() != InteractionHand.OFF_HAND) {
                helper.fail("the recorded action was not the off hand's: " + recorded.getFirst().action());
                return;
            }
            helper.succeed();
        } finally {
            RecordingSessions.discard(player);
        }
    }

    /**
     * A mock player standing where the action is.
     *
     * <p>A session takes its origin from wherever the player is when it starts, and a mock player
     * is created at the world spawn -- millions of blocks away, where everything in this plot is
     * out of range and dropped without a word.
     */
    private static ServerPlayer recordingPlayerAt(GameTestHelper helper, BlockPos absolute) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(absolute.getX() + 0.5, absolute.getY() + 1.0, absolute.getZ() + 0.5);
        return player;
    }

    /**
     * Using an item on a block is one action, and it remembers what it was used on.
     *
     * <p>Tilling a field recorded a "Use Hoe" and then a "Place Hoe" that could only ever fail:
     * NeoForge wraps every {@code useOn} in block-snapshot capture, so the farmland the hoe leaves
     * behind arrives as a placement with a hoe as the item that placed it. Between them the two
     * capture handlers should cover each interaction exactly once.
     */
    private static void tillingRecordsOneAction(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);
        // A hoe refuses to till anything with a block on top of it.
        helper.setBlock(target.above(), Blocks.AIR);

        BlockPos absolute = helper.absolutePos(target);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // A mock player is placed at the world spawn, and a session takes its origin from wherever
        // the player is standing when it starts. Left there, every action in this plot is millions
        // of blocks out of range and is dropped without a word.
        player.snapTo(absolute.getX() + 0.5, absolute.getY() + 1.0, absolute.getZ() + 0.5);

        RecordingSession session = RecordingSessions.start(player);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HOE));
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));

            // If the hoe did not till, this test is about nothing.
            helper.assertBlockPresent(Blocks.FARMLAND, target);

            List<TimedAction> recorded = session.finish().actions();
            if (recorded.size() != 1) {
                helper.fail("one swing of a hoe recorded " + recorded.size() + " actions: "
                        + recorded.stream().map(a -> a.action().type().toString()).toList());
                return;
            }
            if (!(recorded.getFirst().action() instanceof ChronoAction.UseOnBlock use)) {
                helper.fail("tilling was recorded as " + recorded.getFirst().action().type()
                        + " rather than a use on a block");
                return;
            }
            // Dirt, not farmland: what it was used on, not what it left behind.
            if (use.expectedBlock().map(block -> block.value() != Blocks.DIRT).orElse(true)) {
                helper.fail("the hoe did not remember tilling dirt: " + use.expectedBlock());
                return;
            }
            helper.succeed();
        } finally {
            RecordingSessions.discard(player);
        }
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
            ContainerWatch.noteInteraction(player, absolute, session);

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

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

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
