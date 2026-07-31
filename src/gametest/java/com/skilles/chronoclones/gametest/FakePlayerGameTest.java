package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The player an anchor acts as, and what it is allowed to remember between actions.
 *
 * <p>Every anchor used to share one instance per owner, resetting only the held item and the
 * experience. Anything else an action set -- an effect, a fire, a half-finished use -- was still
 * set for the next action, and for every other anchor that player owned.
 */
final class FakePlayerGameTest {

    private FakePlayerGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("fake_player_is_not_shared_between_anchors",
                FakePlayerGameTest::isNotSharedBetweenAnchors);
        ChronoclonesGameTests.add("fake_player_state_does_not_survive_an_action",
                FakePlayerGameTest::stateDoesNotSurviveAnAction);
    }

    private static final BlockPos FIRST = new BlockPos(4, 1, 8);
    private static final BlockPos SECOND = new BlockPos(12, 1, 8);

    /** Two anchors, one owner: still two players, so neither can inherit the other's state. */
    private static void isNotSharedBetweenAnchors(GameTestHelper helper) {
        ChronoAnchorBlockEntity first = miningAnchor(helper, FIRST);
        ChronoAnchorBlockEntity second = miningAnchor(helper, SECOND);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    FakePlayer one = first.getActor().current(0);
                    FakePlayer two = second.getActor().current(0);
                    if (one == null || two == null) {
                        helper.fail("an anchor ran for twenty ticks without ever acting: "
                                + (one == null ? "first" : "second") + " has no player");
                        return;
                    }
                    if (one == two) {
                        helper.fail("two anchors owned by the same player shared one fake player, "
                                + "so anything either of them sets is set for both");
                    }
                })
                .thenSucceed();
    }

    /**
     * State applied to the player mid-action is gone by the time the next action starts.
     *
     * <p>Applied from outside rather than by an item, because the point is the contract rather than
     * any particular item that happens to exercise it: whatever a modded item sets, this is what is
     * supposed to clear it.
     */
    private static void stateDoesNotSurviveAnAction(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = miningAnchor(helper, FIRST);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    FakePlayer actor = current(helper, anchor);
                    // The kinds of thing an item leaves behind: an effect, a fire, a wound, a
                    // half-finished use, and a stance.
                    actor.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
                    actor.setRemainingFireTicks(200);
                    actor.setTicksFrozen(200);
                    actor.setHealth(1.0f);
                    actor.setAbsorptionAmount(8.0f);
                    actor.setSprinting(true);

                    // Something left to mine, or the routine simply reports an empty square from
                    // here on and never acquires the player again -- which would pass this test
                    // without ever running the reset it is about.
                    helper.setBlock(FIRST.above(), Blocks.STONE);
                })
                .thenExecuteAfter(30, () -> {
                    FakePlayer actor = current(helper, anchor);
                    if (!actor.getActiveEffects().isEmpty()) {
                        helper.fail("an effect outlived the action that applied it: "
                                + actor.getActiveEffects());
                    }
                    if (actor.getRemainingFireTicks() > 0 || actor.getTicksFrozen() > 0) {
                        helper.fail("the player is still burning or frozen a whole action later");
                    }
                    if (actor.getHealth() != actor.getMaxHealth()) {
                        helper.fail("the player carried a wound into the next action, at "
                                + actor.getHealth() + " health");
                    }
                    if (actor.getAbsorptionAmount() != 0.0f) {
                        helper.fail("absorption outlived the action that granted it");
                    }
                    if (actor.isSprinting()) {
                        helper.fail("the player is still sprinting between actions");
                    }
                })
                .thenSucceed();
    }

    private static FakePlayer current(GameTestHelper helper, ChronoAnchorBlockEntity anchor) {
        FakePlayer actor = anchor.getActor().current(0);
        if (actor == null) {
            helper.fail("the anchor has not acted yet, so there is nothing to check");
            throw new IllegalStateException("unreachable");
        }
        return actor;
    }

    /**
     * An anchor that breaks a stone block over and over, which is simply something to be doing.
     */
    private static ChronoAnchorBlockEntity miningAnchor(GameTestHelper helper, BlockPos anchorPos) {
        BlockPos target = anchorPos.above();
        helper.setBlock(target, Blocks.STONE);

        ChronoAction.BreakBlock breaking = new ChronoAction.BreakBlock(
                new BlockPos(0, 1, 0),
                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE),
                new ItemStack(Items.NETHERITE_PICKAXE));

        return AnchorTestFixture.placeAndImprint(helper, anchorPos, new Recording(
                List.of(new MotionSample(0, new Vec3(0, 1, 0), 0f, 0f)),
                List.of(new TimedAction(1, breaking, ActionSettings.DEFAULT)),
                10, AnchorTestFixture.AUTHOR_NAME, AnchorTestFixture.AUTHOR_ID));
    }
}
