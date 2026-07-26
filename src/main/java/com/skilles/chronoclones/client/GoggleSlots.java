package com.skilles.chronoclones.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.skilles.chronoclones.client.preview.GoggleCache;
import com.skilles.chronoclones.client.preview.PreviewCache;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.replay.TransferPrecision;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Which slots of an open container some nearby anchor's routine works on.
 *
 * <p>The counterpart to the recording highlights: same squares, same colours, opposite direction in
 * time. That one shows what a session is capturing as you do it; this shows what an imprinted routine
 * will do to the container you have just opened — "this anchor takes from these two slots", without
 * having to read a tooltip and count.
 *
 * <p>Entirely client-side. The goggles already delivered these routines, so working out which of them
 * mentions this block is arithmetic on data the client has, not a question worth a packet.
 */
public final class GoggleSlots {

    private GoggleSlots() {}

    /**
     * The slots one routine touches in one container.
     *
     * @param touched the squares its clicks name
     * @param carried the squares it stocks, and what it expects to find in each
     */
    public record Session(Set<Integer> touched, Map<Integer, Expect> carried) {}

    /**
     * What a routine expects of one square, and how much of that it insists on.
     *
     * <p>The flags come from the anchor rather than the recording, so two anchors running the same
     * routine can be marked up differently — which is the point of them being per anchor.
     */
    public record Expect(ItemStack stack, TransferPrecision precision) {}

    /**
     * The session for the container the player has open, or null if nothing nearby uses it.
     *
     * <p>Sessions from every visible anchor are merged. Two anchors sharing a chest is a normal way
     * to build, and showing only one of them would be showing the wrong half of what happens there.
     */
    public static @Nullable Session sessionFor(AbstractContainerScreen<?> screen) {
        BlockPos open = openContainerPos();
        if (open == null) {
            return null;
        }
        return collect(GoggleCache.current(), open, screen.getMenu().slots.size());
    }

    /**
     * The same answer, from the anchors and the container rather than from the game's state.
     *
     * <p>Split out because this is the part with rules in it — which routines count, and what happens
     * when two of them want the same square — and because a screen and a live hit result are a great
     * deal of world to stand up in order to ask a question about a list.
     */
    static @Nullable Session collect(List<PreviewCache.Target> anchors, BlockPos open, int menuSize) {
        Set<Integer> touched = new HashSet<>();
        Map<Integer, Expect> carried = new HashMap<>();

        for (PreviewCache.Target target : anchors) {
            for (TimedAction timed : target.recording().actions()) {
                if (!(timed.action() instanceof ChronoAction.UseContainer session)) {
                    continue;
                }
                if (!target.placement().toWorld(session.localPos()).equals(open)) {
                    continue;
                }
                // A recorded session against a differently shaped menu describes squares that are
                // not the ones on screen. Replay refuses that case; so should the highlight.
                if (session.menuSize() != menuSize) {
                    continue;
                }
                for (ChronoAction.UseContainer.Click click : session.clicks()) {
                    touched.add(click.slot());
                }
                for (ChronoAction.UseContainer.CarrierSlot slot : session.carrier()) {
                    // First anchor to claim a square wins. Two of them stocking the same one is a
                    // conflict the player should sort out, and averaging the marks would hide it.
                    carried.putIfAbsent(slot.menuSlot(),
                            new Expect(slot.stack(), target.precision()));
                }
            }
        }

        return touched.isEmpty() && carried.isEmpty() ? null : new Session(touched, carried);
    }

    /**
     * The block whose menu is open.
     *
     * <p>Taken from what the player is looking at, because a container screen does not carry its own
     * position — the menu knows about slots, not about the world. In practice you are still pointing
     * at the block you just right-clicked, and if you are not, showing nothing is the correct answer
     * rather than a guess.
     */
    private static @Nullable BlockPos openContainerPos() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }
}
