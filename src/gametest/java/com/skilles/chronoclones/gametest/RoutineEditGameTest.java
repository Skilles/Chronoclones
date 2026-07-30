package com.skilles.chronoclones.gametest;

import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.item.ActionIcons;
import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.item.RecordingDetail;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.network.AnchorAuthority;
import com.skilles.chronoclones.registry.ModItems;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.SessionStep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Editing how an anchor reads its routine, and who may.
 */
final class RoutineEditGameTest {

    private RoutineEditGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("edited_settings_reach_the_running_routine",
                RoutineEditGameTest::editedSettingsReachTheRoutine);
        ChronoclonesGameTests.add("reinterpreting_does_not_restart_the_clones",
                RoutineEditGameTest::reinterpretingDoesNotRestartClones);
        ChronoclonesGameTests.add("only_the_owner_may_reinterpret",
                RoutineEditGameTest::onlyTheOwnerMayReinterpret);
        ChronoclonesGameTests.add("discarding_leaves_the_anchor_blank",
                RoutineEditGameTest::discardingLeavesTheAnchorBlank);
        ChronoclonesGameTests.add("deleting_an_action_leaves_the_others_where_they_were",
                RoutineEditGameTest::deletingAnActionKeepsTheRest);
        ChronoclonesGameTests.add("a_skipped_step_moves_nothing_and_its_neighbours_still_run",
                RoutineEditGameTest::skippedStepMovesNothing);
        ChronoclonesGameTests.add("a_step_carries_only_what_it_is_told_to",
                RoutineEditGameTest::stepCarriesOnlyItsItem);
        ChronoclonesGameTests.add("a_step_finds_its_item_in_another_square",
                RoutineEditGameTest::stepFindsItsItemElsewhere);
        ChronoclonesGameTests.add("a_step_told_exactly_where_looks_nowhere_else",
                RoutineEditGameTest::exactStepLooksNowhereElse);
        ChronoclonesGameTests.add("a_step_told_to_move_one_moves_one",
                RoutineEditGameTest::cappedStepMovesPartOfIt);
        ChronoclonesGameTests.add("an_action_about_a_creature_is_pictured_as_that_creature",
                RoutineEditGameTest::creatureActionsArePicturedAsCreatures);
        ChronoclonesGameTests.add("discarding_hands_back_what_the_clones_were_holding",
                RoutineEditGameTest::discardingSpillsTheStorage);
        ChronoclonesGameTests.add("a_blank_anchor_has_no_storage_to_reach",
                RoutineEditGameTest::blankAnchorHasNoStorage);
        ChronoclonesGameTests.add("a_blank_recorder_takes_a_recording_back_out",
                RoutineEditGameTest::blankRecorderTakesTheRecordingBack);
        ChronoclonesGameTests.add("a_row_names_itself_after_its_options",
                RoutineEditGameTest::rowsNameThemselvesAfterTheirOptions);
    }

    /**
     * Crouching with an empty recorder takes the recording out, leaving the anchor blank.
     *
     * <p>Driven through {@code gameMode.useItemOn} rather than by calling the anchor's own method,
     * because the routing is what was broken: vanilla skips a block's {@code useItemOn} entirely
     * for a crouching player holding anything, so the anchor never saw this and nothing happened.
     * A test that called {@code extractRecording} directly passed the whole time.
     */
    private static void blankRecorderTakesTheRecordingBack(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.DIAMOND), 3);

        int actions = anchor.getRecording().actions().size();
        FakePlayer player = AnchorTestFixture.owner(helper.getLevel());
        crouchOnto(helper, player, new ItemStack(ModItems.CHRONO_RECORDER.get()), ANCHOR);

        try {
            if (anchor.getRecording() != null) {
                helper.fail("crouching with a blank recorder left the recording on the anchor");
                return;
            }
            Recording taken = recordingCarriedBy(player);
            if (taken == null || taken.actions().size() != actions) {
                helper.fail("the recording did not come back out whole");
                return;
            }
            // Taking it out is a removal like any other, so the storage comes with it.
            if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                helper.fail("the storage stayed shut inside an anchor with nothing to run");
                return;
            }

            // And a second try finds nothing to take, rather than handing over a blank recording.
            crouchOnto(helper, player, new ItemStack(ModItems.CHRONO_RECORDER.get()), ANCHOR);
            if (anchor.extractRecording() != null) {
                helper.fail("a blank anchor handed over a second recording");
                return;
            }
            helper.succeed();
        } finally {
            // The fake player for an owner is shared by every test in this level.
            player.getInventory().clearContent();
        }
    }

    /**
     * A row is named after what it does, and renames itself when an option changes what that is.
     *
     * <p>Asserted on the translation key rather than the words, because a server has no language
     * loaded and the words are the one part of this a translator is meant to change.
     */
    private static void rowsNameThemselvesAfterTheirOptions(GameTestHelper helper) {
        TimedAction breaking = new TimedAction(1, new ChronoAction.BreakBlock(
                new BlockPos(0, 0, -1),
                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBBLESTONE),
                new ItemStack(Items.NETHERITE_PICKAXE)));

        // Recorded on cobblestone, so it is the cobblestone one.
        if (!keyOf(RecordingDetail.title(breaking)).equals("gui.chronoclones.editor.name.break")) {
            helper.fail("a break recorded on cobblestone was not named after it: "
                    + keyOf(RecordingDetail.title(breaking)));
            return;
        }

        // Widened to any block, and no longer entitled to name one.
        TimedAction widened = breaking.withSettings(
                ActionSettings.DEFAULT.withRecordedSubject(false));
        if (!keyOf(RecordingDetail.title(widened)).equals("gui.chronoclones.editor.name.break.any")) {
            helper.fail("a break widened to any block kept the name of one: "
                    + keyOf(RecordingDetail.title(widened)));
            return;
        }

        // A name of the player's own outlives every option they touch afterwards.
        TimedAction named = breaking.withSettings(
                ActionSettings.DEFAULT.withName("Cobble farm").withRecordedSubject(false));
        if (!RecordingDetail.title(named).getString().equals("Cobble farm")) {
            helper.fail("changing an option overwrote a name the player typed: "
                    + RecordingDetail.title(named).getString());
            return;
        }
        helper.succeed();
    }

    /** The translation key a component was built from, or its literal contents. */
    private static String keyOf(Component component) {
        return component.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey()
                : component.getString();
    }

    /** Uses an item on a block the way a crouching player does, through the server's own path. */
    private static void crouchOnto(GameTestHelper helper, FakePlayer player, ItemStack held,
                                   BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relativePos);

        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setShiftKeyDown(true);
        try {
            player.gameMode.useItemOn(player, level, player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        } finally {
            // The hand is left alone: what comes back goes into the inventory, and the emptied
            // hand is the likeliest square for it. The caller clears the lot afterwards.
            player.setShiftKeyDown(false);
        }
    }

    /** The first recording on any recorder the player is carrying. */
    private static @org.jspecify.annotations.Nullable Recording recordingCarriedBy(FakePlayer player) {
        for (ItemStack stack : player.getInventory()) {
            Recording recording = ChronoRecorderItem.recordingOf(stack);
            if (recording != null) {
                return recording;
            }
        }
        return null;
    }

    /**
     * The storage belongs to the recording that filled it.
     *
     * <p>Left inside a blank anchor it is unreachable in every sense: the squares are shut, and a
     * player with no recording to imprint has no way to open them again.
     */
    private static void discardingSpillsTheStorage(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.DIAMOND), 7);
        anchor.setCloneExperience(1, new com.skilles.chronoclones.block.ExperienceStore(40));

        anchor.clearRecording();

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(ANCHOR);

        helper.startSequence()
                .thenExecuteAfter(6, () -> {
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                        helper.fail("a discarded recording left its diamonds shut inside the anchor");
                    }
                    if (!anchor.getCloneExperience(1).isEmpty()) {
                        helper.fail("a discarded recording kept its banked experience");
                    }

                    int diamonds = level.getEntitiesOfClass(ItemEntity.class,
                                    new AABB(absolute).inflate(3.0)).stream()
                            .filter(item -> item.getItem().is(Items.DIAMOND))
                            .mapToInt(item -> item.getItem().getCount())
                            .sum();
                    if (diamonds != 7) {
                        helper.fail("expected seven diamonds handed back, found " + diamonds);
                    }
                    // At least: a neighbouring test's orbs can drift into any box wide enough for
                    // the spill's own random motion.
                    int points = level.getEntitiesOfClass(ExperienceOrb.class,
                                    new AABB(absolute).inflate(3.0)).stream()
                            .mapToInt(ExperienceOrb::getValue).sum();
                    if (points < 40) {
                        helper.fail("expected the banked experience back, found " + points);
                    }
                })
                .thenSucceed();
    }

    /** Shut squares, so nothing can be put somewhere it would be stranded. */
    private static void blankAnchorHasNoStorage(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        ChronoAnchorMenu menu = new ChronoAnchorMenu(1,
                helper.makeMockServerPlayerInLevel().getInventory(), anchor,
                anchor.getContainerData());
        if (!menu.hasStorage()) {
            helper.fail("an imprinted anchor refused its own storage");
        }

        anchor.clearRecording();
        if (menu.hasStorage()) {
            helper.fail("a blank anchor still offers squares nothing can come out of");
        }
        helper.succeed();
    }

    /**
     * The half of the icon choice a unit test cannot reach: finding a creature's spawn egg reads
     * item components, which are not bound until a datapack has loaded.
     */
    private static void creatureActionsArePicturedAsCreatures(GameTestHelper helper) {
        // A bucket says nothing about which cow was milked, so the cow is what is shown.
        assertIcon(helper, new ChronoAction.InteractEntity(
                        Vec3.ZERO, BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW),
                        InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(Items.BUCKET)),
                Items.COW_SPAWN_EGG);

        assertIcon(helper, new ChronoAction.UseContainer(
                        new MenuTarget.Entity(Vec3.ZERO,
                                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.VILLAGER)),
                        39, List.of(), List.of()),
                Items.VILLAGER_SPAWN_EGG);

        helper.succeed();
    }

    private static void assertIcon(GameTestHelper helper, ChronoAction action,
                                   net.minecraft.world.item.Item expected) {
        net.minecraft.world.item.Item shown = ActionIcons.of(action)
                .map(net.minecraft.core.Holder::value)
                .orElse(null);
        if (shown != expected) {
            helper.fail("expected " + expected + " to stand for " + action.type() + ", got " + shown);
        }
    }

    /** An item filter is how a routine stops hauling whatever happens to be in the square. */
    private static void stepCarriesOnlyItsItem(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = sendingAnchor(helper, 0, Items.DIAMOND,
                ActionSettings.StepSettings.DEFAULT.withItems(
                        List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.EMERALD))));
        stock(helper.getLevel(), helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR)),
                0, Items.DIAMOND, 5);

        helper.startSequence()
                .thenExecuteAfter(20, () -> assertHolds(helper, anchor, Items.DIAMOND, 0,
                        "a filter for emeralds let diamonds through"))
                .thenSucceed();
    }

    /** Stock rarely lands back where it was, so the recorded square is a hint by default. */
    private static void stepFindsItsItemElsewhere(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = sendingAnchor(helper, 3, Items.DIAMOND,
                ActionSettings.StepSettings.DEFAULT);
        // Recorded coming out of slot 3, actually sitting in slot 7.
        stock(helper.getLevel(), helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR)),
                7, Items.DIAMOND, 5);

        helper.startSequence()
                .thenExecuteAfter(20, () -> assertHolds(helper, anchor, Items.DIAMOND, 5,
                        "the diamonds moved one square along and the step gave up"))
                .thenSucceed();
    }

    /** The same, told to sort as it works: the recorded square or nothing. */
    private static void exactStepLooksNowhereElse(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = sendingAnchor(helper, 3, Items.DIAMOND,
                ActionSettings.StepSettings.DEFAULT.withSlot(
                        new SlotRule(SlotRule.Mode.EXACT, SlotRule.NONE)));
        stock(helper.getLevel(), helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR)),
                7, Items.DIAMOND, 5);

        helper.startSequence()
                .thenExecuteAfter(20, () -> assertHolds(helper, anchor, Items.DIAMOND, 0,
                        "a step told to use one square only went looking anyway"))
                .thenSucceed();
    }

    /** Told to move one, it moves one, whatever the recording happened to move. */
    private static void cappedStepMovesPartOfIt(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        BlockPos absolute = helper.absolutePos(target);
        stock(helper.getLevel(), absolute, 0, Items.DIAMOND, 12);

        // Slot to slot inside the barrel, so what stays behind is visible in the barrel itself.
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), 27 + 36, List.of(),
                                List.of(new SessionStep.Move(0, 9,
                                        BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND),
                                        SessionStep.Amount.ALL))),
                        ActionSettings.DEFAULT.withStep(0,
                                ActionSettings.StepSettings.DEFAULT.withAmount(
                                        java.util.Optional.of(SessionStep.Amount.ONE)))));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    ResourceHandler<ItemResource> barrel = helper.getLevel().getCapability(
                            Capabilities.Item.BLOCK, absolute, null);
                    if (barrel == null) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (barrel.getAmountAsInt(9) != 1) {
                        helper.fail("expected one moved of a stack of twelve, slot 9 holds "
                                + barrel.getAmountAsInt(9));
                    }
                    if (barrel.getAmountAsInt(0) != 11) {
                        helper.fail("the rest was not put back, slot 0 holds "
                                + barrel.getAmountAsInt(0));
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                        helper.fail("the remainder came home with the clone");
                    }
                })
                .thenSucceed();
    }

    /**
     * An anchor whose one step shift-clicks {@code item} out of {@code from}, under one step rule.
     */
    private static ChronoAnchorBlockEntity sendingAnchor(GameTestHelper helper, int from,
                                                         net.minecraft.world.item.Item item,
                                                         ActionSettings.StepSettings rule) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.BARREL);

        return AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), 27 + 36, List.of(),
                                List.of(send(from, item))),
                        ActionSettings.DEFAULT.withStep(0, rule)));
    }

    private static void assertHolds(GameTestHelper helper, ChronoAnchorBlockEntity anchor,
                                    net.minecraft.world.item.Item item, int expected, String what) {
        int held = AnchorTestFixture.countIn(anchor.getInventory(), item);
        if (held != expected) {
            helper.fail(what + ": expected " + expected + ", the anchor holds " + held);
        }
    }

    /** Deleting one action is not the same as re-timing the rest of the routine. */
    private static void deletingAnActionKeepsTheRest(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(List.of(
                        useOn(new BlockPos(0, 0, -1)),
                        useOn(new BlockPos(0, 0, -2)),
                        useOn(new BlockPos(0, 0, -3)))));

        Recording before = anchor.getRecording();
        Recording after = before.without(1);

        if (after.actions().size() != 2) {
            helper.fail("expected two actions left of three, got " + after.actions().size());
        }
        if (after.lengthTicks() != before.lengthTicks()) {
            helper.fail("deleting an action shortened the routine from " + before.lengthTicks()
                    + " to " + after.lengthTicks() + " ticks");
        }
        // The survivors keep their own ticks, so what is left happens when it always did.
        if (after.actions().get(0).tick() != before.actions().get(0).tick()
                || after.actions().get(1).tick() != before.actions().get(2).tick()) {
            helper.fail("the surviving actions were re-timed by the deletion");
        }

        UUID stranger = UUID.fromString("c0000000-0000-0000-0000-00000000000c");
        if (AnchorAuthority.mayRetune(anchor.getOwnerId(), stranger)) {
            helper.fail("a stranger was allowed to delete from somebody else's routine");
        }
        helper.succeed();
    }

    /**
     * A step turned off is how one is dropped without re-performing the whole routine to get it back.
     */
    private static void skippedStepMovesNothing(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(target);
        stock(level, absolute, 0, Items.DIAMOND, 5);
        stock(level, absolute, 1, Items.EMERALD, 5);

        // Two shift-clicks out of the barrel, the first of which is switched off.
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), 27 + 36, List.of(),
                                List.of(send(0, Items.DIAMOND), send(1, Items.EMERALD))),
                        ActionSettings.DEFAULT.withStep(0,
                                ActionSettings.StepSettings.DEFAULT.withEnabled(false))));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                        helper.fail("the skipped step ran anyway: the anchor holds "
                                + AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND)
                                + " diamonds");
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.EMERALD) != 5) {
                        helper.fail("the step after the skipped one did not run: the anchor holds "
                                + AnchorTestFixture.countIn(anchor.getInventory(), Items.EMERALD)
                                + " emeralds");
                    }
                })
                .thenSucceed();
    }

    private static SessionStep send(int from, net.minecraft.world.item.Item item) {
        return new SessionStep.Move(from, SessionStep.Move.ELSEWHERE,
                BuiltInRegistries.ITEM.wrapAsHolder(item), SessionStep.Amount.ALL);
    }

    private static ChronoAction useOn(BlockPos localPos) {
        return new ChronoAction.UseOnBlock(localPos, Direction.UP, new Vec3(0.0, 0.5, 0.0), false,
                InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(Items.AIR));
    }

    private static void stock(ServerLevel level, BlockPos absolutePos, int slot,
                              net.minecraft.world.item.Item item, int amount) {
        ResourceHandler<ItemResource> handler =
                level.getCapability(Capabilities.Item.BLOCK, absolutePos, null);
        if (handler == null) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(slot, ItemResource.of(item), amount, tx);
            tx.commit();
        }
    }

    /** What the editor's discard button reaches, once the payload has checked who is asking. */
    private static void discardingLeavesTheAnchorBlank(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.clearRecording();

        if (anchor.getRecording() != null) {
            helper.fail("the anchor kept its routine after being told to discard it");
        }
        if (helper.getBlockState(ANCHOR).getValue(
                com.skilles.chronoclones.block.ChronoAnchorBlock.ACTIVE)) {
            helper.fail("a blank anchor is still lit as though it were running");
        }
        helper.succeed();
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** An edit is only worth anything if the running anchor reads it. */
    private static void editedSettingsReachTheRoutine(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        Recording edited = anchor.getRecording().withSettings(0, ActionSettings.DEFAULT
                .withName("Quarry the north face")
                .withSlot(new SlotRule(SlotRule.Mode.EXACT, 3)));
        anchor.reinterpret(edited);

        ActionSettings settings = anchor.getRecording().actions().getFirst().settings();
        if (!"Quarry the north face".equals(settings.name())) {
            helper.fail("the name did not survive the edit: " + settings.name());
        }
        if (settings.slot().mode() != SlotRule.Mode.EXACT || settings.slot().slot() != 3) {
            helper.fail("the slot rule did not survive the edit: " + settings.slot());
        }
        helper.succeed();
    }

    /**
     * Only the interpretation changes, so the clones must not be rebuilt out from under themselves.
     */
    private static void reinterpretingDoesNotRestartClones(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    int before = anchor.getContainerData().get(
                            com.skilles.chronoclones.menu.AnchorData.playhead(0));
                    anchor.reinterpret(anchor.getRecording().withSettings(0,
                            ActionSettings.DEFAULT.withName("Renamed mid-stride")));

                    int after = anchor.getContainerData().get(
                            com.skilles.chronoclones.menu.AnchorData.playhead(0));
                    if (after != before) {
                        helper.fail("an edit moved the playhead from " + before + " to " + after);
                    }
                })
                .thenSucceed();
    }

    /** The authority check the edit payload leans on, which is the anchor's only protection. */
    private static void onlyTheOwnerMayReinterpret(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        UUID stranger = UUID.fromString("c0000000-0000-0000-0000-00000000000c");
        if (AnchorAuthority.mayRetune(anchor.getOwnerId(), stranger)) {
            helper.fail("a stranger was allowed to retune somebody else's anchor");
        }
        if (!AnchorAuthority.mayRetune(anchor.getOwnerId(), AnchorTestFixture.OWNER_ID)) {
            helper.fail("the owner was refused their own anchor");
        }
        helper.succeed();
    }
}
