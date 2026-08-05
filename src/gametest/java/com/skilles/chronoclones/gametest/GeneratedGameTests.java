//? if <26 {
/*package com.skilles.chronoclones.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

// GENERATED from data/chronoclones/test_instance/*.json - regenerate rather than edit.
// Pre-26 versions have no data-driven test instances, so every declared test gets an
// annotated shim here that dispatches back into the shared function table.
*///?}
//? if <26 {
//? if neoforge {
/*@net.neoforged.neoforge.gametest.GameTestHolder(com.skilles.chronoclones.Chronoclones.MODID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
*///?}
//?}
//? if <26 {
/*public class GeneratedGameTests {

    // NeoForge prefixes the holder namespace onto template ids; Fabric wants the full id.
*///?}
//? if <26 {
//? if neoforge {
/*    private static final String PLOT = "test_plot";
*///?} else {
/*    private static final String PLOT = "chronoclones:test_plot";
*///?}
//?}
//? if <26 {
/*


    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void a_blank_anchor_has_no_storage_to_reach(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_blank_anchor_has_no_storage_to_reach", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void a_blank_recorder_takes_a_recording_back_out(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_blank_recorder_takes_a_recording_back_out", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void a_comparator_reads_running_and_stopped_apart(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_comparator_reads_running_and_stopped_apart", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void a_container_opened_with_the_recorder_in_hand_is_still_watched(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_container_opened_with_the_recorder_in_hand_is_still_watched", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void a_container_step_that_fails_names_its_step(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_container_step_that_fails_names_its_step", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void a_finished_action_is_reported_as_run(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_finished_action_is_reported_as_run", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void a_halt_leaves_the_rest_marked_not_reached(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_halt_leaves_the_rest_marked_not_reached", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void a_held_signal_keeps_the_routine_looping(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_held_signal_keeps_the_routine_looping", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void a_hopper_cannot_feed_an_anchor_before_an_imprint(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_hopper_cannot_feed_an_anchor_before_an_imprint", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void a_missing_block_is_reported_skipped_with_its_reason(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_missing_block_is_reported_skipped_with_its_reason", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void a_passing_main_hand_does_not_shadow_the_off_hand(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_passing_main_hand_does_not_shadow_the_off_hand", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void a_real_comparator_hears_about_the_wind_down(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_real_comparator_hears_about_the_wind_down", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void a_recorded_session_remembers_what_it_opened(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_recorded_session_remembers_what_it_opened", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void a_redstone_pulse_runs_one_cycle_and_stops(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_redstone_pulse_runs_one_cycle_and_stops", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void a_refused_interaction_records_nothing(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_refused_interaction_records_nothing", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void a_refused_item_use_records_nothing(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_refused_item_use_records_nothing", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void a_right_drag_takes_one_item_out_of_a_stack(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_right_drag_takes_one_item_out_of_a_stack", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void a_rising_edge_resumes_a_paused_anchor(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_rising_edge_resumes_a_paused_anchor", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void a_session_on_a_plain_block_says_there_is_nothing_to_open(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_session_on_a_plain_block_says_there_is_nothing_to_open", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void a_skipped_step_moves_nothing_and_its_neighbours_still_run(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_skipped_step_moves_nothing_and_its_neighbours_still_run", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void a_starved_anchor_reads_stalled_on_a_comparator(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_starved_anchor_reads_stalled_on_a_comparator", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void a_step_carries_only_what_it_is_told_to(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_step_carries_only_what_it_is_told_to", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void a_step_finds_its_item_in_another_square(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_step_finds_its_item_in_another_square", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void a_step_told_exactly_where_looks_nowhere_else(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_step_told_exactly_where_looks_nowhere_else", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void a_step_told_to_move_one_moves_one(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_step_told_to_move_one_moves_one", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void a_stopped_anchor_still_shows_its_storage_tabs(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_stopped_anchor_still_shows_its_storage_tabs", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void a_wind_down_reads_below_full_on_a_comparator(GameTestHelper helper) {
        ChronoclonesGameTests.run("a_wind_down_reads_below_full_on_a_comparator", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void an_action_about_a_creature_is_pictured_as_that_creature(GameTestHelper helper) {
        ChronoclonesGameTests.run("an_action_about_a_creature_is_pictured_as_that_creature", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void an_anchor_told_to_ignore_redstone_ignores_it(GameTestHelper helper) {
        ChronoclonesGameTests.run("an_anchor_told_to_ignore_redstone_ignores_it", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void an_unimprinted_anchor_refuses_a_shift_click(GameTestHelper helper) {
        ChronoclonesGameTests.run("an_unimprinted_anchor_refuses_a_shift_click", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void anchor_drops_with_routine(GameTestHelper helper) {
        ChronoclonesGameTests.run("anchor_drops_with_routine", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void anchor_session_says_so_when_the_page_is_gone(GameTestHelper helper) {
        ChronoclonesGameTests.run("anchor_session_says_so_when_the_page_is_gone", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void anchor_session_stocks_another_anchor(GameTestHelper helper) {
        ChronoclonesGameTests.run("anchor_session_stocks_another_anchor", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void anvil_drinks_a_bottle_to_afford_the_work(GameTestHelper helper) {
        ChronoclonesGameTests.run("anvil_drinks_a_bottle_to_afford_the_work", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void anvil_names_what_it_is_given(GameTestHelper helper) {
        ChronoclonesGameTests.run("anvil_names_what_it_is_given", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void anvil_without_banked_experience_says_so(GameTestHelper helper) {
        ChronoclonesGameTests.run("anvil_without_banked_experience_says_so", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void any_slot_rule_ignores_the_recorded_square(GameTestHelper helper) {
        ChronoclonesGameTests.run("any_slot_rule_ignores_the_recorded_square", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void attack_finds_a_target_that_moved(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_finds_a_target_that_moved", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void attack_misses_beyond_its_radius(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_misses_beyond_its_radius", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void attack_needs_a_weapon_it_owns(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_needs_a_weapon_it_owns", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void attack_returns_the_weapon_it_borrowed(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_returns_the_weapon_it_borrowed", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void attack_smart_picks_the_hardest_hitter(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_smart_picks_the_hardest_hitter", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void attack_spares_a_creature_it_did_not_record(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_spares_a_creature_it_did_not_record", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void attack_until_dead_finishes_the_kill(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_until_dead_finishes_the_kill", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 240)
    public void attack_until_dead_gives_up_eventually(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_until_dead_gives_up_eventually", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void attack_widened_takes_whatever_is_there(GameTestHelper helper) {
        ChronoclonesGameTests.run("attack_widened_takes_whatever_is_there", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void attribution_resolves_to_owner(GameTestHelper helper) {
        ChronoclonesGameTests.run("attribution_resolves_to_owner", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void author_is_never_the_actor(GameTestHelper helper) {
        ChronoclonesGameTests.run("author_is_never_the_actor", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void author_survives_imprint(GameTestHelper helper) {
        ChronoclonesGameTests.run("author_survives_imprint", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void blacklisted_block_survives(GameTestHelper helper) {
        ChronoclonesGameTests.run("blacklisted_block_survives", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void block_entities_are_never_broken(GameTestHelper helper) {
        ChronoclonesGameTests.run("block_entities_are_never_broken", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void break_bare_hands_clear_soft_blocks(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_bare_hands_clear_soft_blocks", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void break_digs_with_the_anchors_own_tool(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_digs_with_the_anchors_own_tool", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void break_is_instant_from_a_creative_recording(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_is_instant_from_a_creative_recording", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void break_needs_the_tool_in_the_anchor(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_needs_the_tool_in_the_anchor", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void break_refuses_a_block_it_was_not_recorded_on(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_refuses_a_block_it_was_not_recorded_on", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void break_stores_drops_in_anchor(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_stores_drops_in_anchor", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 400)
    public void break_takes_time_in_survival(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_takes_time_in_survival", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 400)
    public void break_whatever_is_in_the_square(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_whatever_is_in_the_square", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void break_with_a_poor_tool_is_slow_not_refused(GameTestHelper helper) {
        ChronoclonesGameTests.run("break_with_a_poor_tool_is_slow_not_refused", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void broken_anchor_gives_back_banked_experience(GameTestHelper helper) {
        ChronoclonesGameTests.run("broken_anchor_gives_back_banked_experience", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void broken_anchor_spills_inventory(GameTestHelper helper) {
        ChronoclonesGameTests.run("broken_anchor_spills_inventory", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void carrier_clicks_the_square_it_recorded(GameTestHelper helper) {
        ChronoclonesGameTests.run("carrier_clicks_the_square_it_recorded", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void carrier_item_rule_holds_back_what_it_names(GameTestHelper helper) {
        ChronoclonesGameTests.run("carrier_item_rule_holds_back_what_it_names", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void carrier_lends_every_square_it_has(GameTestHelper helper) {
        ChronoclonesGameTests.run("carrier_lends_every_square_it_has", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void carrier_lends_the_hotbar_row_too(GameTestHelper helper) {
        ChronoclonesGameTests.run("carrier_lends_the_hotbar_row_too", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void carrier_lends_the_square_the_click_names(GameTestHelper helper) {
        ChronoclonesGameTests.run("carrier_lends_the_square_the_click_names", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void carrier_quantity_rule_caps_what_it_lends(GameTestHelper helper) {
        ChronoclonesGameTests.run("carrier_quantity_rule_caps_what_it_lends", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void carrier_returns_to_the_square_it_lent_from(GameTestHelper helper) {
        ChronoclonesGameTests.run("carrier_returns_to_the_square_it_lent_from", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void carrier_stack_survives_an_imprint(GameTestHelper helper) {
        ChronoclonesGameTests.run("carrier_stack_survives_an_imprint", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void carries_on_when_the_block_changed(GameTestHelper helper) {
        ChronoclonesGameTests.run("carries_on_when_the_block_changed", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void choosing_one_offer_twice_is_one_step(GameTestHelper helper) {
        ChronoclonesGameTests.run("choosing_one_offer_twice_is_one_step", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void clone_cannot_reach_another_inventory(GameTestHelper helper) {
        ChronoclonesGameTests.run("clone_cannot_reach_another_inventory", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void container_deposits_into_a_container(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_deposits_into_a_container", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void container_full_slot_leaves_the_item_alone(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_full_slot_leaves_the_item_alone", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void container_loads_a_furnace(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_loads_a_furnace", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void container_moves_within_itself(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_moves_within_itself", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void container_refuses_another_menu(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_refuses_another_menu", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void container_runs_understocked(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_runs_understocked", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void container_shift_clicks_out(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_shift_clicks_out", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void container_splits_a_stack_by_intent(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_splits_a_stack_by_intent", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void container_untouched_stock_comes_home(GameTestHelper helper) {
        ChronoclonesGameTests.run("container_untouched_stock_comes_home", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void discarding_hands_back_what_the_clones_were_holding(GameTestHelper helper) {
        ChronoclonesGameTests.run("discarding_hands_back_what_the_clones_were_holding", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void discarding_leaves_the_anchor_blank(GameTestHelper helper) {
        ChronoclonesGameTests.run("discarding_leaves_the_anchor_blank", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void each_clone_draws_from_its_own_inventory(GameTestHelper helper) {
        ChronoclonesGameTests.run("each_clone_draws_from_its_own_inventory", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void edited_settings_reach_the_running_routine(GameTestHelper helper) {
        ChronoclonesGameTests.run("edited_settings_reach_the_running_routine", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void every_change_to_the_routine_bumps_its_revision(GameTestHelper helper) {
        ChronoclonesGameTests.run("every_change_to_the_routine_bumps_its_revision", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void exact_slot_rule_refuses_to_search(GameTestHelper helper) {
        ChronoclonesGameTests.run("exact_slot_rule_refuses_to_search", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void fake_player_is_not_shared_between_anchors(GameTestHelper helper) {
        ChronoclonesGameTests.run("fake_player_is_not_shared_between_anchors", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void fake_player_state_does_not_survive_an_action(GameTestHelper helper) {
        ChronoclonesGameTests.run("fake_player_state_does_not_survive_an_action", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void full_inventory_does_not_destroy(GameTestHelper helper) {
        ChronoclonesGameTests.run("full_inventory_does_not_destroy", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void held_slot_falls_back_to_a_search(GameTestHelper helper) {
        ChronoclonesGameTests.run("held_slot_falls_back_to_a_search", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void held_slot_is_drawn_from_first(GameTestHelper helper) {
        ChronoclonesGameTests.run("held_slot_is_drawn_from_first", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void held_use_draws_and_looses_a_bow(GameTestHelper helper) {
        ChronoclonesGameTests.run("held_use_draws_and_looses_a_bow", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void held_use_needs_the_item_it_recorded(GameTestHelper helper) {
        ChronoclonesGameTests.run("held_use_needs_the_item_it_recorded", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void held_use_returns_the_bow_afterwards(GameTestHelper helper) {
        ChronoclonesGameTests.run("held_use_returns_the_bow_afterwards", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void item_match_exact_wants_the_components(GameTestHelper helper) {
        ChronoclonesGameTests.run("item_match_exact_wants_the_components", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void item_match_same_kind_is_the_default(GameTestHelper helper) {
        ChronoclonesGameTests.run("item_match_same_kind_is_the_default", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void legacy_inventory_loads_into_the_first_clone(GameTestHelper helper) {
        ChronoclonesGameTests.run("legacy_inventory_loads_into_the_first_clone", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void losing_the_signal_lets_the_cycle_finish_first(GameTestHelper helper) {
        ChronoclonesGameTests.run("losing_the_signal_lets_the_cycle_finish_first", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void menu_refuses_a_page_with_no_clone(GameTestHelper helper) {
        ChronoclonesGameTests.run("menu_refuses_a_page_with_no_clone", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void menu_selection_needs_only_synced_data(GameTestHelper helper) {
        ChronoclonesGameTests.run("menu_selection_needs_only_synced_data", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void mined_loot_fills_the_hotbar_first(GameTestHelper helper) {
        ChronoclonesGameTests.run("mined_loot_fills_the_hotbar_first", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void mined_ore_banks_its_experience(GameTestHelper helper) {
        ChronoclonesGameTests.run("mined_ore_banks_its_experience", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void mined_stone_banks_nothing(GameTestHelper helper) {
        ChronoclonesGameTests.run("mined_stone_banks_nothing", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void move_step_drops_a_single_item(GameTestHelper helper) {
        ChronoclonesGameTests.run("move_step_drops_a_single_item", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void move_step_over_an_empty_square_does_nothing(GameTestHelper helper) {
        ChronoclonesGameTests.run("move_step_over_an_empty_square_does_nothing", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void move_step_sends_a_stack_elsewhere(GameTestHelper helper) {
        ChronoclonesGameTests.run("move_step_sends_a_stack_elsewhere", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void move_step_takes_half_of_what_is_there(GameTestHelper helper) {
        ChronoclonesGameTests.run("move_step_takes_half_of_what_is_there", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void nudge_cannot_extend_reach(GameTestHelper helper) {
        ChronoclonesGameTests.run("nudge_cannot_extend_reach", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void nudge_moves_where_the_routine_acts(GameTestHelper helper) {
        ChronoclonesGameTests.run("nudge_moves_where_the_routine_acts", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void pausing_holds_the_playhead_where_it_was(GameTestHelper helper) {
        ChronoclonesGameTests.run("pausing_holds_the_playhead_where_it_was", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void place_widened_to_any_block_builds_with_what_it_has(GameTestHelper helper) {
        ChronoclonesGameTests.run("place_widened_to_any_block_builds_with_what_it_has", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void placement_keeps_the_click_that_made_it(GameTestHelper helper) {
        ChronoclonesGameTests.run("placement_keeps_the_click_that_made_it", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void placement_replays_the_state_it_recorded(GameTestHelper helper) {
        ChronoclonesGameTests.run("placement_replays_the_state_it_recorded", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void playing_after_a_pause_carries_on(GameTestHelper helper) {
        ChronoclonesGameTests.run("playing_after_a_pause_carries_on", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void protection_can_cancel(GameTestHelper helper) {
        ChronoclonesGameTests.run("protection_can_cancel", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void pulled_splitter_spills_its_clone(GameTestHelper helper) {
        ChronoclonesGameTests.run("pulled_splitter_spills_its_clone", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void recording_ignores_own_clones(GameTestHelper helper) {
        ChronoclonesGameTests.run("recording_ignores_own_clones", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void reinterpreting_does_not_restart_the_clones(GameTestHelper helper) {
        ChronoclonesGameTests.run("reinterpreting_does_not_restart_the_clones", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void session_finds_a_villager_that_wandered(GameTestHelper helper) {
        ChronoclonesGameTests.run("session_finds_a_villager_that_wandered", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void shard_imprint_uses_new_owner(GameTestHelper helper) {
        ChronoclonesGameTests.run("shard_imprint_uses_new_owner", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void shard_is_not_consumed_by_imprint(GameTestHelper helper) {
        ChronoclonesGameTests.run("shard_is_not_consumed_by_imprint", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void shard_preserves_author(GameTestHelper helper) {
        ChronoclonesGameTests.run("shard_preserves_author", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void shift_click_stays_on_the_visible_page(GameTestHelper helper) {
        ChronoclonesGameTests.run("shift_click_stays_on_the_visible_page", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void smart_tool_falls_back_to_bare_hands(GameTestHelper helper) {
        ChronoclonesGameTests.run("smart_tool_falls_back_to_bare_hands", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void smart_tool_picks_something_the_recording_never_held(GameTestHelper helper) {
        ChronoclonesGameTests.run("smart_tool_picks_something_the_recording_never_held", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void smart_tool_refuses_to_break_for_nothing(GameTestHelper helper) {
        ChronoclonesGameTests.run("smart_tool_refuses_to_break_for_nothing", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void smelted_result_banks_the_furnace_experience(GameTestHelper helper) {
        ChronoclonesGameTests.run("smelted_result_banks_the_furnace_experience", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void standing_power_read_again_after_a_reload_is_not_an_edge(GameTestHelper helper) {
        ChronoclonesGameTests.run("standing_power_read_again_after_a_reload_is_not_an_edge", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void stopping_takes_the_clones_away_and_starts_over(GameTestHelper helper) {
        ChronoclonesGameTests.run("stopping_takes_the_clones_away_and_starts_over", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void the_fuel_slot_takes_only_what_will_burn(GameTestHelper helper) {
        ChronoclonesGameTests.run("the_fuel_slot_takes_only_what_will_burn", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void the_redstone_mode_and_latch_survive_a_reload(GameTestHelper helper) {
        ChronoclonesGameTests.run("the_redstone_mode_and_latch_survive_a_reload", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void the_report_names_the_clone_that_tried(GameTestHelper helper) {
        ChronoclonesGameTests.run("the_report_names_the_clone_that_tried", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void the_upgrade_slots_take_only_upgrades(GameTestHelper helper) {
        ChronoclonesGameTests.run("the_upgrade_slots_take_only_upgrades", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void tilling_records_one_action_and_the_block_it_worked(GameTestHelper helper) {
        ChronoclonesGameTests.run("tilling_records_one_action_and_the_block_it_worked", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones0", timeoutTicks = 200)
    public void trade_refuses_an_offer_that_is_gone(GameTestHelper helper) {
        ChronoclonesGameTests.run("trade_refuses_an_offer_that_is_gone", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones1", timeoutTicks = 200)
    public void trade_says_so_when_the_merchant_is_sold_out(GameTestHelper helper) {
        ChronoclonesGameTests.run("trade_says_so_when_the_merchant_is_sold_out", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones2", timeoutTicks = 200)
    public void trade_survives_its_offers_being_reordered(GameTestHelper helper) {
        ChronoclonesGameTests.run("trade_survives_its_offers_being_reordered", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones3", timeoutTicks = 200)
    public void use_needs_its_item_in_the_anchor(GameTestHelper helper) {
        ChronoclonesGameTests.run("use_needs_its_item_in_the_anchor", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones4", timeoutTicks = 200)
    public void use_on_block_flips_a_lever(GameTestHelper helper) {
        ChronoclonesGameTests.run("use_on_block_flips_a_lever", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones5", timeoutTicks = 200)
    public void use_on_block_refuses_a_block_it_was_not_recorded_on(GameTestHelper helper) {
        ChronoclonesGameTests.run("use_on_block_refuses_a_block_it_was_not_recorded_on", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones6", timeoutTicks = 200)
    public void use_on_block_widened_works_whatever_is_there(GameTestHelper helper) {
        ChronoclonesGameTests.run("use_on_block_widened_works_whatever_is_there", helper);
    }

    @GameTest(template = PLOT, batch = "chronoclones7", timeoutTicks = 200)
    public void use_returns_what_it_borrowed(GameTestHelper helper) {
        ChronoclonesGameTests.run("use_returns_what_it_borrowed", helper);
    }
}
*///?}
