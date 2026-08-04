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

final class AttackIntentGameTest {

    private AttackIntentGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("attack_finds_a_target_that_moved",
                AttackIntentGameTest::findsATargetThatMoved);
        ChronoclonesGameTests.add("attack_misses_beyond_its_radius",
                AttackIntentGameTest::missesBeyondItsRadius);
        ChronoclonesGameTests.add("attack_until_dead_finishes_the_kill",
                AttackIntentGameTest::untilDeadFinishesTheKill);
        ChronoclonesGameTests.add("attack_until_dead_gives_up_eventually", 240,
                AttackIntentGameTest::untilDeadGivesUpEventually);
        ChronoclonesGameTests.add("attack_needs_a_weapon_it_owns",
                AttackIntentGameTest::needsAWeaponItOwns);
        ChronoclonesGameTests.add("attack_returns_the_weapon_it_borrowed",
                AttackIntentGameTest::returnsTheWeaponItBorrowed);
        ChronoclonesGameTests.add("attack_smart_picks_the_hardest_hitter",
                AttackIntentGameTest::smartPicksTheHardestHitter);
        ChronoclonesGameTests.add("attack_spares_a_creature_it_did_not_record",
                AttackIntentGameTest::sparesACreatureItDidNotRecord);
        ChronoclonesGameTests.add("attack_widened_takes_whatever_is_there",
                AttackIntentGameTest::widenedTakesWhateverIsThere);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static final BlockPos RECORDED = new BlockPos(0, 0, -1);

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

    private static void untilDeadFinishesTheKill(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north());
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper,
                untilDead(), 400);

        CowTrace trace = new CowTrace();

        helper.startSequence()
                .thenExecuteFor(WINDOW, () -> trace.sample(helper.getLevel(), cow))
                .thenExecute(() -> {
                    if (cow.isAlive()) {
                        helper.fail("the cow survived an attack told to finish it, at "
                                + cow.getHealth() + " health, reporting "
                                + anchor.getLastFailure().reason() + "\n" + trace.report());
                    }
                })
                .thenSucceed();
    }

    private static final int WINDOW = 160;

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

    private static void untilDeadGivesUpEventually(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north());
        cow.setInvulnerable(true);
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper,
                untilDead(), 400);

        helper.startSequence()
                // The give-up lands one tick after maxActionTicks (default 160) of waiting.
                .thenExecuteAfter(190, () -> {
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.UNFINISHED) {
                        helper.fail("expected the attack to give up and say so, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void needsAWeaponItOwns(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north());
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper, TargetRule.DEFAULT);
        takeEverythingBack(anchor);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (cow.getHealth() < cow.getMaxHealth()) {
                        helper.fail("an anchor holding no sword swung one anyway, for "
                                + (cow.getMaxHealth() - cow.getHealth()) + " damage");
                    }
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_ITEM) {
                        helper.fail("expected the swing to report having no weapon, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void returnsTheWeaponItBorrowed(GameTestHelper helper) {
        spawn(helper, ANCHOR.north());
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper, TargetRule.DEFAULT);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    ItemStack sword = AnchorTestFixture.findStack(anchor.getInventory(),
                            Items.NETHERITE_SWORD);
                    if (sword == null) {
                        helper.fail("the anchor swung its sword and never got it back");
                    }
                    if (sword.getDamageValue() == 0) {
                        helper.fail("the sword came back untouched, so the swing skipped durability");
                    }
                })
                .thenSucceed();
    }

    private static void smartPicksTheHardestHitter(GameTestHelper helper) {
        Mob cow = spawn(helper, ANCHOR.north());
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper,
                TargetRule.DEFAULT, 20, ActionSettings.ToolRule.SMART);
        takeEverythingBack(anchor);
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.WOODEN_SHOVEL, 1));
        anchor.getCloneInventory(0).setItem(1, new ItemStack(Items.DIAMOND_AXE, 1));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    ItemStack axe = AnchorTestFixture.findStack(anchor.getInventory(),
                            Items.DIAMOND_AXE);
                    if (axe == null || axe.getDamageValue() == 0) {
                        helper.fail("a smart swing left the axe alone and reached for something else");
                    }
                    if (cow.getHealth() >= cow.getMaxHealth()) {
                        helper.fail("a smart swing with an axe in the anchor did nothing at all");
                    }
                })
                .thenSucceed();
    }

    private static void sparesACreatureItDidNotRecord(GameTestHelper helper) {
        Mob pig = spawnPig(helper, ANCHOR.north());
        ChronoAnchorBlockEntity anchor = attackingAnchor(helper, TargetRule.DEFAULT);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (pig.getHealth() < pig.getMaxHealth()) {
                        helper.fail("a routine recorded against a cow attacked a pig, for "
                                + (pig.getMaxHealth() - pig.getHealth()) + " damage");
                    }
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_TARGET) {
                        helper.fail("expected the swing to report no cow, got "
                                + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void widenedTakesWhateverIsThere(GameTestHelper helper) {
        Mob pig = spawnPig(helper, ANCHOR.north());
        attackingAnchor(helper, TargetRule.DEFAULT, 20, ActionSettings.ToolRule.EXACT, false);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (pig.getHealth() >= pig.getMaxHealth()) {
                        helper.fail("an attack widened to any creature still refused the pig");
                    }
                })
                .thenSucceed();
    }

    private static Mob spawnPig(GameTestHelper helper, BlockPos relative) {
        Mob pig = EntityTypes.PIG.spawn(helper.getLevel(), helper.absolutePos(relative),
                EntitySpawnReason.TRIGGERED);
        if (pig == null) {
            helper.fail("could not spawn the pig this test is about");
            throw new IllegalStateException("unreachable");
        }
        pig.setNoAi(true);
        return pig;
    }

    private static void takeEverythingBack(ChronoAnchorBlockEntity anchor) {
        for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
            var inventory = anchor.getCloneInventory(clone);
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static TargetRule untilDead() {
        return TargetRule.DEFAULT.withCompletion(TargetRule.Completion.UNTIL_DEAD);
    }

    private static Mob spawn(GameTestHelper helper, BlockPos relative) {
        Mob cow = EntityTypes.COW.spawn(helper.getLevel(), helper.absolutePos(relative),
                EntitySpawnReason.TRIGGERED);
        if (cow == null) {
            helper.fail("could not spawn the cow this test is about");
            throw new IllegalStateException("unreachable");
        }
        cow.setNoAi(true);
        return cow;
    }

    private static ChronoAnchorBlockEntity attackingAnchor(GameTestHelper helper, TargetRule rule) {
        return attackingAnchor(helper, rule, 20);
    }

    private static ChronoAnchorBlockEntity attackingAnchor(GameTestHelper helper, TargetRule rule,
                                                           int length) {
        return attackingAnchor(helper, rule, length, ActionSettings.ToolRule.EXACT);
    }

    private static ChronoAnchorBlockEntity attackingAnchor(GameTestHelper helper, TargetRule rule,
                                                           int length,
                                                           ActionSettings.ToolRule weapon) {
        return attackingAnchor(helper, rule, length, weapon, true);
    }

    private static ChronoAnchorBlockEntity attackingAnchor(GameTestHelper helper, TargetRule rule,
                                                           int length,
                                                           ActionSettings.ToolRule weapon,
                                                           boolean recorded) {
        ChronoAction.AttackEntity swing = new ChronoAction.AttackEntity(
                Vec3.atCenterOf(RECORDED),
                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW),
                new ItemStack(Items.NETHERITE_SWORD));

        return AnchorTestFixture.placeAndImprint(helper, ANCHOR, new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, swing, ActionSettings.DEFAULT
                        .withTarget(rule).withTool(weapon).withRecordedSubject(recorded))),
                length, AnchorTestFixture.AUTHOR_NAME, AnchorTestFixture.AUTHOR_ID));
    }
}
