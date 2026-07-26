package com.skilles.chronoclones.gametest;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.Recording;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * the security-critical tests: <b>attribution must resolve from the anchor owner, never
 * from the recording author.</b>
 *
 * <p>The spec is explicit that getting this backwards is a griefing vector — a crafted recording
 * handed to another player would break blocks in the author's name and pass their land claims. It
 * asks for a test before shipping multiplayer, and this is it.
 *
 * <p>These run in a real server with real events, which is the only place the claim can actually be
 * checked: the whole question is what a third-party protection mod observes, and that cannot be
 * asserted off-runtime.
 */
final class AttributionGameTest {

    private AttributionGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("attribution_resolves_to_owner", AttributionGameTest::attributionResolvesToOwner);
        ChronoclonesGameTests.add("author_is_never_the_actor", AttributionGameTest::authorIsNeverTheActor);
        ChronoclonesGameTests.add("protection_can_cancel", AttributionGameTest::protectionCanCancel);
        ChronoclonesGameTests.add("author_survives_imprint", AttributionGameTest::authorSurvivesImprint);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** The identity a protection mod would see on the break event. */
    private static void attributionResolvesToOwner(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        AtomicReference<UUID> observed = new AtomicReference<>();
        Object listener = listenForBreakAt(helper.absolutePos(target),
                event -> observed.compareAndSet(null, event.getPlayer().getUUID()));

        AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    NeoForge.EVENT_BUS.unregister(listener);

                    UUID actor = observed.get();
                    if (actor == null) {
                        helper.fail("no block break event fired — the routine never executed");
                    }
                    if (!AnchorTestFixture.OWNER_ID.equals(actor)) {
                        helper.fail("break was attributed to " + actor + " but must be the anchor owner "
                                + AnchorTestFixture.OWNER_ID);
                    }
                })
                .thenSucceed();
    }

    /**
     * The same assertion stated as the failure it prevents: the author must never be the actor,
     * even though the routine is theirs.
     */
    private static void authorIsNeverTheActor(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        AtomicInteger authorAttributedBreaks = new AtomicInteger();
        Object listener = listenForBreakAt(helper.absolutePos(target), event -> {
            if (AnchorTestFixture.AUTHOR_ID.equals(event.getPlayer().getUUID())) {
                authorAttributedBreaks.incrementAndGet();
            }
        });

        AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    NeoForge.EVENT_BUS.unregister(listener);
                    if (authorAttributedBreaks.get() > 0) {
                        helper.fail("the recording author was used as the actor "
                                + authorAttributedBreaks.get() + " time(s) - this is the spec 10.1 griefing vector");
                    }
                })
                .thenSucceed();
    }

    /**
     * Stands in for a protection mod or land claim: a listener that cancels the break must actually
     * stop it, and the anchor must report why rather than failing silently.
     */
    private static void protectionCanCancel(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        Object listener = listenForBreakAt(helper.absolutePos(target), event -> event.setCanceled(true));

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    NeoForge.EVENT_BUS.unregister(listener);

                    helper.assertBlockPresent(Blocks.STONE, target);

                    DiagnosticState failure = anchor.getLastFailure();
                    if (failure.reason() != DiagnosticState.FailureReason.PROTECTED) {
                        helper.fail("expected a PROTECTED diagnostic after a cancelled break, got "
                                + failure.reason());
                    }
                })
                .thenSucceed();
    }

    /** Imprinting takes ownership without rewriting who authored the routine. */
    private static void authorSurvivesImprint(GameTestHelper helper) {
        Recording recording = AnchorTestFixture.breakOneBlock(Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR, recording);

        Recording stored = anchor.getRecording();
        if (stored == null) {
            helper.fail("anchor did not keep the imprinted recording");
            return;
        }
        if (!AnchorTestFixture.AUTHOR_ID.equals(stored.authorId())) {
            helper.fail("author was rewritten on imprint: expected " + AnchorTestFixture.AUTHOR_ID
                    + " but got " + stored.authorId());
        }
        if (!AnchorTestFixture.OWNER_ID.equals(anchor.getOwnerId())) {
            helper.fail("anchor owner should be the imprinting player, got " + anchor.getOwnerId());
        }
        helper.succeed();
    }

    /**
     * Registers a temporary game-bus listener scoped to one absolute position.
     *
     * <p>The position filter is not optional. Game tests share a world and run concurrently, so an
     * unfiltered listener observes breaks from <em>other</em> tests' anchors and asserts against
     * whichever fired first. That makes a test either flaky or — worse — passing for the wrong
     * reason, because the neighbouring test happened to use the same owner.
     */
    private static Object listenForBreakAt(BlockPos absolutePos,
                                           java.util.function.Consumer<BreakBlockEvent> handler) {
        Object listener = new Object() {
            @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
            public void onBreak(BreakBlockEvent event) {
                if (event.getPos().equals(absolutePos)) {
                    handler.accept(event);
                }
            }
        };
        NeoForge.EVENT_BUS.register(listener);
        return listener;
    }
}
