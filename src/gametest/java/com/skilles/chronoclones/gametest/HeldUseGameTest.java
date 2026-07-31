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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Items that have to be held down rather than clicked.
 *
 * <p>Every one of these used to record as an instant use and replay as one: the clone clicked, the
 * fake player was let go of on the same tick, and nothing was ever drawn, eaten or blocked. A bow
 * is the clearest case, because whether it fired at all is visible in the world.
 */
final class HeldUseGameTest {

    private HeldUseGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("held_use_draws_and_looses_a_bow", HeldUseGameTest::drawsAndLoosesABow);
        ChronoclonesGameTests.add("held_use_returns_the_bow_afterwards",
                HeldUseGameTest::returnsTheBowAfterwards);
        ChronoclonesGameTests.add("held_use_needs_the_item_it_recorded",
                HeldUseGameTest::needsTheItemItRecorded);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    /** Twenty ticks is a full draw, which is what a player firing a bow properly does. */
    private static final int FULL_DRAW = 20;

    private static void drawsAndLoosesABow(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = bowAnchor(helper, FULL_DRAW);
        stock(anchor);

        helper.startSequence()
                // Long enough for the click, the draw, and the release.
                .thenExecuteAfter(40, () -> {
                    if (arrowsNear(helper) == 0) {
                        helper.fail("the anchor held a bow for " + FULL_DRAW
                                + " ticks and never loosed anything");
                    }
                })
                .thenSucceed();
    }

    /** The bow is lent for the draw, not given: it comes back, and the arrow is spent. */
    private static void returnsTheBowAfterwards(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = bowAnchor(helper, FULL_DRAW);
        stock(anchor);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.BOW) == 0) {
                        helper.fail("the anchor drew its bow and never got it back");
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.ARROW) != 3) {
                        helper.fail("expected one of four arrows spent, "
                                + AnchorTestFixture.countIn(anchor.getInventory(), Items.ARROW)
                                + " left");
                    }
                })
                .thenSucceed();
    }

    /** No bow in the anchor, no shot: a held use is stocked like any other. */
    private static void needsTheItemItRecorded(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = bowAnchor(helper, FULL_DRAW);
        // Arrows but no bow, so there is something to fire and nothing to fire it with.
        anchor.getCloneInventory(0).set(0, ItemResource.of(new ItemStack(Items.ARROW)), 4);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    if (arrowsNear(helper) > 0) {
                        helper.fail("an anchor holding no bow fired one anyway");
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------- helpers

    private static int arrowsNear(GameTestHelper helper) {
        AABB plot = helper.getBounds().inflate(2.0);
        return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, plot).size();
    }

    private static void stock(ChronoAnchorBlockEntity anchor) {
        anchor.getCloneInventory(0).set(0, ItemResource.of(new ItemStack(Items.BOW)), 1);
        anchor.getCloneInventory(0).set(1, ItemResource.of(new ItemStack(Items.ARROW)), 4);
    }

    /**
     * An anchor whose routine draws a bow for {@code holdTicks} and lets go.
     */
    private static ChronoAnchorBlockEntity bowAnchor(GameTestHelper helper, int holdTicks) {
        ChronoAction.UseItem drawing = new ChronoAction.UseItem(
                InteractionHand.MAIN_HAND,
                BuiltInRegistries.ITEM.wrapAsHolder(Items.BOW),
                holdTicks);

        // Longer than the draw, so the routine cannot loop back round mid-shot and confuse the
        // question this is asking.
        return AnchorTestFixture.placeAndImprint(helper, ANCHOR, new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, drawing, ActionSettings.DEFAULT)),
                200, AnchorTestFixture.AUTHOR_NAME, AnchorTestFixture.AUTHOR_ID));
    }
}
