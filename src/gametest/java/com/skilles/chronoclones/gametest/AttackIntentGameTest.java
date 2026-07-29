package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Attacks that pursue an outcome rather than repeating a count of swings.
 */
final class AttackIntentGameTest {

    private AttackIntentGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("attack_finds_a_target_that_moved",
                AttackIntentGameTest::findsATargetThatMoved);
        ChronoclonesGameTests.add("attack_misses_beyond_its_radius",
                AttackIntentGameTest::missesBeyondItsRadius);
        ChronoclonesGameTests.add("attack_until_dead_finishes_the_kill",
                AttackIntentGameTest::untilDeadFinishesTheKill);
        ChronoclonesGameTests.add("attack_until_dead_gives_up_eventually",
                AttackIntentGameTest::untilDeadGivesUpEventually);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** The point the routine swings at, one step north of the anchor. */
    private static final BlockPos RECORDED = new BlockPos(0, 0, -1);

    /**
     * A mob two blocks from where it was swung at is still within a player's reach.
     */
    private static void findsATargetThatMoved(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north().north().north());
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper, TargetRule.DEFAULT);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (cow.getHealth() >= cow.getMaxHealth()) {
                        helper.fail("the cow wandered two blocks and the routine lost it entirely");
                    }
                    if (anchor.getLastFailure().reason() == DiagnosticState.FailureReason.NO_TARGET) {
                        helper.fail("the routine reported nothing to attack with a cow in reach");
                    }
                })
                .thenSucceed();
    }

    /** The radius is what stops a clone being a turret, so it has to actually bound the search. */
    private static void missesBeyondItsRadius(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north().north().north());
        attackingAnchor(helper, TargetRule.DEFAULT.withRadius(1.0));

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    if (cow.getHealth() < cow.getMaxHealth()) {
                        helper.fail("a one-block radius reached a cow two blocks away");
                    }
                })
                .thenSucceed();
    }

    /**
     * The whole point: the player killed it, so the clone kills it, however many swings that takes.
     */
    private static void untilDeadFinishesTheKill(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north());
        // A routine longer than the test window, so nothing but holding can land a second swing.
        attackingAnchor(helper, TargetRule.DEFAULT.withCompletion(TargetRule.Completion.UNTIL_DEAD), 400);

        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    if (cow.isAlive()) {
                        helper.fail("the cow survived an attack told to finish it, at "
                                + cow.getHealth() + " health");
                    }
                })
                .thenSucceed();
    }

    /** A target that cannot die must not hold the routine forever. */
    private static void untilDeadGivesUpEventually(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north());
        cow.setInvulnerable(true);
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper,
                TargetRule.DEFAULT.withCompletion(TargetRule.Completion.UNTIL_DEAD), 400);

        helper.startSequence()
                // The cap is 100 ticks; this is comfortably past it.
                .thenExecuteAfter(130, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.UNFINISHED) {
                        helper.fail("expected the attack to give up and say so, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------- helpers

    private static Mob spawn(GameTestHelper helper, BlockPos relative) {
        Mob cow = EntityTypes.COW.spawn(helper.getLevel(), helper.absolutePos(relative),
                EntitySpawnReason.TRIGGERED);
        if (cow == null) {
            helper.fail("could not spawn the cow this test is about");
            throw new IllegalStateException("unreachable");
        }
        // Standing still, so the test is about the routine rather than about pathfinding.
        cow.setNoAi(true);
        return cow;
    }

    private static ChronoAnchorBlockEntity attackingAnchor(GameTestHelper helper, TargetRule rule) {
        return attackingAnchor(helper, rule, 20);
    }

    /**
     * @param length how long the routine runs before looping, which is what separates an action
     *               that held the timeline from one the next loop simply repeated
     */
    private static ChronoAnchorBlockEntity attackingAnchor(GameTestHelper helper, TargetRule rule,
                                                           int length) {
        ChronoAction.AttackEntity swing = new ChronoAction.AttackEntity(
                Vec3.atCenterOf(RECORDED),
                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW),
                new ItemStack(Items.NETHERITE_SWORD));

        return AnchorTestFixture.placeAndImprint(helper, ANCHOR, new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, swing, ActionSettings.DEFAULT.withTarget(rule))),
                length, AnchorTestFixture.AUTHOR_NAME, AnchorTestFixture.AUTHOR_ID));
    }
}
