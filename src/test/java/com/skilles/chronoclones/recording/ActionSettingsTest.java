package com.skilles.chronoclones.recording;

import java.util.List;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;

import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionSettingsTest {

    @Test
    @DisplayName("finishing a target off keeps that target, whatever the lock flag says")
    void untilDeadLocksItsTarget() {
        TargetRule wandering = TargetRule.DEFAULT
                .withCompletion(TargetRule.Completion.UNTIL_DEAD)
                .withSticky(false);

        assertTrue(wandering.locksTarget(),
                "an until-dead attack that re-picks each swing leaves a pen of half-hurt animals");
    }

    @Test
    @DisplayName("a single swing only keeps its target when it was asked to")
    void onceLocksOnlyWhenAsked() {
        TargetRule once = TargetRule.DEFAULT.withCompletion(TargetRule.Completion.ONCE);

        assertFalse(once.locksTarget());
        assertTrue(once.withSticky(true).locksTarget());
    }

    @Test
    @DisplayName("an unedited action prefers its recorded square and searches on from there")
    void defaultsAreWhatThePlayerDid() {
        SlotRule rule = SlotRule.prefer(4);

        assertEquals(4, rule.preferred());
        assertFalse(rule.strict(), "the default must fall back, or stock landing one square over stalls");
    }

    @Test
    @DisplayName("an exact rule refuses to look anywhere else")
    void exactRuleDoesNotSearch() {
        SlotRule rule = new SlotRule(SlotRule.Mode.EXACT, 4);

        assertEquals(4, rule.preferred());
        assertTrue(rule.strict());
    }

    @Test
    @DisplayName("an any rule has no square to prefer")
    void anyRuleStartsAtTheBeginning() {
        SlotRule rule = new SlotRule(SlotRule.Mode.ANY, 4);

        assertEquals(SlotRule.NONE, rule.preferred(), "an any rule still named a square to start at");
        assertFalse(rule.strict());
    }

    @Test
    @DisplayName("an empty entity filter accepts anything, so an unedited attack behaves as before")
    void emptyFilterAcceptsAnything() {
        assertTrue(TargetRule.DEFAULT.accepts(EntityTypes.ZOMBIE));
        assertTrue(TargetRule.DEFAULT.accepts(EntityTypes.COW));
    }

    @Test
    @DisplayName("a filter admits only what it names")
    void filterAdmitsOnlyWhatItNames() {
        TargetRule rule = TargetRule.DEFAULT.withFilter(List.of(
                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.ZOMBIE)));

        assertTrue(rule.accepts(EntityTypes.ZOMBIE));
        assertFalse(rule.accepts(EntityTypes.COW));
    }

    @Test
    @DisplayName("a setting may narrow the anchor's reach but never extend it")
    void radiusIsCappedByTheAnchor() {
        assertEquals(8.0, TargetRule.DEFAULT.withRadius(64.0).radiusWithin(8));
        assertEquals(2.0, TargetRule.DEFAULT.withRadius(2.0).radiusWithin(8));
        assertEquals(0.0, TargetRule.DEFAULT.withRadius(-1.0).radiusWithin(8));
    }

    @Test
    @DisplayName("settings survive a round trip through their codec")
    void settingsRoundTrip() {
        ActionSettings original = ActionSettings.DEFAULT
                .withName("Harvest the north row")
                .withSlot(new SlotRule(SlotRule.Mode.EXACT, 7))
                .withTarget(TargetRule.DEFAULT
                        .withRadius(6.5)
                        .withSticky(true)
                        .withCompletion(TargetRule.Completion.UNTIL_DEAD))
                .withTransfer(ActionSettings.TransferRule.DEFAULT
                        .withItems(List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.BREAD)))
                        .withQuantity(ActionSettings.QuantityRule.atMost(16)));

        var encoded = RecordingCodecs.ACTION_SETTINGS.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();
        ActionSettings decoded = RecordingCodecs.ACTION_SETTINGS
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("an empty item list carries anything, so an unedited session behaves as before")
    void emptyItemListCarriesAnything() {
        assertTrue(ActionSettings.TransferRule.DEFAULT.allows(Items.BREAD));
        assertTrue(ActionSettings.TransferRule.DEFAULT.allows(Items.DIAMOND));
        assertEquals(Integer.MAX_VALUE, ActionSettings.QuantityRule.DEFAULT.budget(),
                "no ceiling means no ceiling, not a ceiling of zero");
    }

    @Test
    @DisplayName("an item list carries only what it names")
    void itemListCarriesOnlyWhatItNames() {
        ActionSettings.TransferRule rule = ActionSettings.TransferRule.DEFAULT
                .withItems(List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.BREAD)));

        assertTrue(rule.allows(Items.BREAD));
        assertFalse(rule.allows(Items.DIAMOND));
    }

    @Test
    @DisplayName("a ceiling of nothing at all is no ceiling, not a refusal to carry")
    void zeroCapIsNoCap() {
        assertEquals(ActionSettings.QuantityRule.DEFAULT, ActionSettings.QuantityRule.atMost(0));
        assertEquals(16, ActionSettings.QuantityRule.atMost(16).budget());
    }

    @Test
    @DisplayName("an action saved before settings existed keeps the square it recorded")
    void legacyHeldSlotBecomesAPreference() {
        JsonObject legacy = JsonParser.parseString("""
                {
                  "tick": 3,
                  "action": { "type": "use_item", "hand": "main_hand", "item": "minecraft:bread" },
                  "held_slot": 6
                }
                """).getAsJsonObject();

        TimedAction decoded = RecordingCodecs.TIMED_ACTION
                .parse(JsonOps.INSTANCE, legacy).getOrThrow();

        assertEquals(3, decoded.tick());
        assertEquals(6, decoded.settings().slot().preferred(),
                "the recorded square was dropped on the way in");
        assertEquals(SlotRule.Mode.PREFER, decoded.settings().slot().mode());
    }

    @Test
    @DisplayName("an action with neither settings nor a held slot decodes to the defaults")
    void missingSettingsDecodeToDefault() {
        JsonObject bare = JsonParser.parseString("""
                {
                  "tick": 1,
                  "action": { "type": "use_item", "hand": "main_hand", "item": "minecraft:bread" }
                }
                """).getAsJsonObject();

        TimedAction decoded = RecordingCodecs.TIMED_ACTION.parse(JsonOps.INSTANCE, bare).getOrThrow();

        assertEquals(ActionSettings.DEFAULT, decoded.settings());
    }

    @Test
    @DisplayName("the legacy field is read but never written back out")
    void legacyFieldIsNotEncoded() {
        TimedAction action = new TimedAction(1, new ChronoAction.UseItem(
                InteractionHand.MAIN_HAND,
                BuiltInRegistries.ITEM.wrapAsHolder(Items.BREAD)), 6);

        JsonObject encoded = RecordingCodecs.TIMED_ACTION
                .encodeStart(JsonOps.INSTANCE, action).getOrThrow().getAsJsonObject();

        assertFalse(encoded.has("held_slot"), "the legacy field was written back out");
        assertTrue(encoded.has("settings"));
    }
}
