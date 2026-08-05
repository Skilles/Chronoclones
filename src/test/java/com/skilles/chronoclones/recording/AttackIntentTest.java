package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.skilles.chronoclones.recording.ActionSettings.TargetRule.Completion;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
//? if >=26 {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackIntentTest {

    private static final UUID ZOMBIE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COW = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static AttackIntent.Swing swing(int tick, UUID target) {
        return new AttackIntent.Swing(new TimedAction(tick, new ChronoAction.AttackEntity(
                new Vec3(0, 0, -1),
                //? if >=26 {
                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.ZOMBIE),
                //?} else {
                /*BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.ZOMBIE),
                *///?}
                ItemStack.EMPTY)), target);
    }

    private static AttackIntent.Swing other(int tick) {
        return AttackIntent.Swing.of(new TimedAction(tick, new ChronoAction.UseItem(
                InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(Items.BREAD))));
    }

    private static Completion completionOf(TimedAction action) {
        return action.settings().target().completion();
    }

    @Test
    @DisplayName("a run of swings at one target that died becomes one action with a goal")
    void aKillBecomesOneAction() {
        List<TimedAction> collapsed = AttackIntent.coalesce(
                List.of(swing(5, ZOMBIE), swing(9, ZOMBIE), swing(13, ZOMBIE)), Set.of(ZOMBIE));

        assertEquals(1, collapsed.size(), "five swings meant one kill");
        assertEquals(5, collapsed.getFirst().tick(), "the clone must start when the player started");
        assertEquals(Completion.UNTIL_DEAD, completionOf(collapsed.getFirst()));
    }

    @Test
    @DisplayName("a run that left the target alive collapses but keeps swinging once")
    void aSurvivorIsNotAGoal() {
        List<TimedAction> collapsed = AttackIntent.coalesce(
                List.of(swing(5, ZOMBIE), swing(9, ZOMBIE)), Set.of());

        assertEquals(1, collapsed.size());
        assertEquals(Completion.ONCE, completionOf(collapsed.getFirst()),
                "nothing died, so nothing licensed the clone to keep going");
    }

    @Test
    @DisplayName("one swing that killed is still a kill")
    void oneSwingCanBeAGoal() {
        List<TimedAction> collapsed = AttackIntent.coalesce(List.of(swing(5, ZOMBIE)), Set.of(ZOMBIE));

        assertEquals(1, collapsed.size());
        assertEquals(Completion.UNTIL_DEAD, completionOf(collapsed.getFirst()),
                "a one-shot kill is still a kill; the next mob may be tougher");
    }

    @Test
    @DisplayName("swings at different targets stay different actions")
    void differentTargetsStaySeparate() {
        List<TimedAction> collapsed = AttackIntent.coalesce(
                List.of(swing(5, ZOMBIE), swing(9, COW), swing(13, ZOMBIE)), Set.of());

        assertEquals(3, collapsed.size(), "three targets in a row is three intentions");
    }

    @Test
    @DisplayName("a run does not reach across something that is not a swing")
    void runsAreAdjacentOnly() {
        List<TimedAction> collapsed = AttackIntent.coalesce(
                List.of(swing(5, ZOMBIE), other(7), swing(9, ZOMBIE)), Set.of(ZOMBIE));

        assertEquals(3, collapsed.size(), "the eat in the middle split the run");
        assertEquals(Completion.UNTIL_DEAD, completionOf(collapsed.getFirst()));
        assertEquals(Completion.UNTIL_DEAD, completionOf(collapsed.get(2)));
    }

    @Test
    @DisplayName("actions that are not swings pass through untouched")
    void nonSwingsPassThrough() {
        List<TimedAction> collapsed = AttackIntent.coalesce(
                List.of(other(1), other(2), other(3)), Set.of(ZOMBIE));

        assertEquals(3, collapsed.size());
        assertEquals(ActionSettings.DEFAULT, collapsed.getFirst().settings());
    }

    @Test
    @DisplayName("an empty recording coalesces to nothing rather than throwing")
    void emptyIsEmpty() {
        assertEquals(List.of(), AttackIntent.coalesce(List.of(), Set.of()));
    }
}
