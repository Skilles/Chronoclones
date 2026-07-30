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
import net.minecraft.server.level.ServerLevel;
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
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper,
                heldTightly(), 400);

        // Sampled every tick, printed on none of them: this test used to fail about one run in ten
        // and the printouts that would have explained it moved the timing enough to hide it.
        CowTrace trace = new CowTrace();

        helper.startSequence()
                // Wide margin: tests share one world, and a busy tick can slow the swing cadence.
                // The 400-tick routine still cannot loop in here, so only holding lands a second hit.
                .thenExecuteFor(WINDOW, () -> trace.sample(helper.getLevel(), cow))
                .thenExecute(() -> {
                    if (cow.isAlive()) {
                        // The reason matters: giving up is a different failure from never swinging,
                        // and one of them is the hold cap being too near this window.
                        helper.fail("the cow survived an attack told to finish it, at "
                                + cow.getHealth() + " health, reporting "
                                + anchor.getLastFailure().reason() + "\n" + trace.report());
                    }
                })
                .thenSucceed();
    }

    private static final int WINDOW = 160;

    /**
     * What one cow looked like on every tick of the window, kept in arrays and read only on failure.
     *
     * <p>Between them these say which of the ways this could go wrong actually happened: a tick
     * count that stops rising means the mob is not being ticked, invulnerability pinned at full
     * means something keeps re-hitting it, and a sawtooth over unchanged health means swings are
     * landing and doing nothing. It was the first of those, which is why the chunk's ticking state
     * is sampled beside them.
     */
    private static final class CowTrace {

        private final int[] tickCount = new int[WINDOW];
        private final float[] health = new float[WINDOW];
        private final int[] invulnerable = new int[WINDOW];
        private final int[] hurtTime = new int[WINDOW];
        private final boolean[] ticking = new boolean[WINDOW];
        private int samples;

        void sample(ServerLevel level, Mob cow) {
            if (samples >= WINDOW) {
                return;
            }
            tickCount[samples] = cow.tickCount;
            health[samples] = cow.getHealth();
            invulnerable[samples] = cow.invulnerableTime;
            hurtTime[samples] = cow.hurtTime;
            ticking[samples] = level.isPositionEntityTicking(cow.blockPosition());
            samples++;
        }

        /** One line per tick that differs from the one before it, so a stall shows as a gap. */
        String report() {
            StringBuilder out = new StringBuilder("tick/health/invulnerable/hurt/chunk-ticks:");
            for (int i = 0; i < samples; i++) {
                boolean changed = i == 0
                        || tickCount[i] != tickCount[i - 1] + 1
                        || health[i] != health[i - 1]
                        || invulnerable[i] != invulnerable[i - 1] - 1
                        || hurtTime[i] != Math.max(0, hurtTime[i - 1] - 1)
                        || ticking[i] != ticking[i - 1];
                if (changed || i == samples - 1) {
                    out.append("\n  ").append(i).append(": ").append(tickCount[i]).append(' ')
                            .append(health[i]).append(' ').append(invulnerable[i]).append(' ')
                            .append(hurtTime[i]).append(' ').append(ticking[i]);
                }
            }
            return out.toString();
        }
    }

    /** A target that cannot die must not hold the routine forever. */
    private static void untilDeadGivesUpEventually(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north());
        cow.setInvulnerable(true);
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper,
                heldTightly(), 400);

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

    /**
     * Until-dead, and reaching no further than the square it was recorded at.
     *
     * <p>The two until-dead tests each hold a clone swinging for a hundred ticks, and every test
     * shares one world: with the default reach, one of them will sooner or later spend that hundred
     * ticks swinging at the other's cow, which is invulnerable and never dies.
     */
    private static TargetRule heldTightly() {
        return TargetRule.DEFAULT
                .withCompletion(TargetRule.Completion.UNTIL_DEAD)
                .withRadius(1.5);
    }

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
