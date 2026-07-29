package com.skilles.chronoclones.gametest;

import java.util.UUID;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.network.AnchorAuthority;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.Recording;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

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
