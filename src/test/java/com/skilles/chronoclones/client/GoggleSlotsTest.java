package com.skilles.chronoclones.client;

import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.client.preview.PreviewCache;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.replay.TransferPrecision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which nearby routines a container's highlights come from, and whose settings they carry.
 *
 * <p>The marks are drawn from anchors the player is not looking at and may not even have noticed, so
 * the ways this goes wrong are all quiet ones: a chest marked up by a routine that works a different
 * chest, or a square marked as pinned because the anchor next to it is the strict one. Neither
 * announces itself — the highlight simply describes something that is not going to happen.
 *
 * <p>Carrier stacks are empty throughout, because a populated one cannot be built without a loaded
 * datapack. Nothing here asserts their contents; what is asserted is which squares are claimed and
 * which anchor's flags they end up carrying.
 */
class GoggleSlotsTest {

    private static final BlockPos CHEST = new BlockPos(10, 64, 10);
    private static final int MENU_SIZE = 27 + 36;

    private static PreviewCache.Target anchor(BlockPos pos, TransferPrecision precision,
                                              int... carrierSlots) {
        return anchor(pos, BlockPos.ZERO, precision, carrierSlots);
    }

    /** An anchor at {@code pos}, north-facing, working the container one step north of its origin. */
    private static PreviewCache.Target anchor(BlockPos pos, BlockPos nudge,
                                              TransferPrecision precision, int... carrierSlots) {
        List<ChronoAction.UseContainer.CarrierSlot> carrier =
                java.util.Arrays.stream(carrierSlots)
                        .mapToObj(slot -> new ChronoAction.UseContainer.CarrierSlot(slot, ItemStack.EMPTY))
                        .toList();

        ChronoAction session = new ChronoAction.UseContainer(
                new BlockPos(0, 0, -1), MENU_SIZE, carrier,
                List.of(new ChronoAction.UseContainer.Click(3, 0, ContainerInput.PICKUP)));

        Recording recording = new Recording(
                List.of(new MotionSample(0, Vec3.ZERO, 0f, 0f)),
                List.of(new TimedAction(1, session)),
                20, "Author", UUID.randomUUID());

        return new PreviewCache.Target(pos, Direction.NORTH, recording, false,
                DiagnosticState.NONE, nudge, precision);
    }

    /** The anchor position whose local (0, 0, -1) lands on the chest, for a north-facing anchor. */
    private static BlockPos anchorFor(BlockPos container) {
        return container.south();
    }

    /**
     * A second anchor working the same chest, from a block higher up and aimed back down at it.
     *
     * <p>Two anchors cannot stand in the same block, so the only way to have two of them working one
     * container is for at least one to be nudged — which also means these tests cover the highlight
     * honouring the nudge, and not only the anchor's own position.
     */
    private static PreviewCache.Target nudgedOntoTheChest(TransferPrecision precision,
                                                          int... carrierSlots) {
        return anchor(anchorFor(CHEST).above(), new BlockPos(0, -1, 0), precision, carrierSlots);
    }

    @Test
    @DisplayName("a routine that works this container claims its squares")
    void matchingRoutineIsCollected() {
        GoggleSlots.Session session = GoggleSlots.collect(
                List.of(anchor(anchorFor(CHEST), TransferPrecision.NONE, 30)), CHEST, MENU_SIZE);

        assertNotNull(session);
        assertTrue(session.carried().containsKey(30), "carried: " + session.carried().keySet());
        assertTrue(session.touched().contains(3));
    }

    @Test
    @DisplayName("a routine working a different container is ignored")
    void otherContainersAreIgnored() {
        // Same routine, an anchor one block over — so its session lands on the chest next door.
        BlockPos elsewhere = anchorFor(CHEST).east();

        assertNull(GoggleSlots.collect(
                List.of(anchor(elsewhere, TransferPrecision.NONE, 30)), CHEST, MENU_SIZE));
    }

    @Test
    @DisplayName("a session recorded against a differently shaped menu is ignored")
    void differentMenuShapeIsIgnored() {
        // Slot indices only mean anything relative to the menu that produced them, so marking up a
        // menu of another shape would point at squares chosen by arithmetic rather than by anyone.
        assertNull(GoggleSlots.collect(
                List.of(anchor(anchorFor(CHEST), TransferPrecision.NONE, 30)), CHEST, 3 + 36));
    }

    @Test
    @DisplayName("each square carries the settings of the anchor that stocks it")
    void everySquareCarriesItsOwnAnchorsSettings() {
        // Two anchors, one chest, different settings — which is the whole reason the flags travel
        // per square rather than per screen. A shared setting would mark one of them as something
        // it is not, and the mark is the only place a player sees this without opening both.
        TransferPrecision strict = new TransferPrecision(true, true, true);

        GoggleSlots.Session session = GoggleSlots.collect(List.of(
                anchor(anchorFor(CHEST), TransferPrecision.NONE, 30),
                nudgedOntoTheChest(strict, 31)), CHEST, MENU_SIZE);

        assertNotNull(session);
        assertEquals(TransferPrecision.NONE, session.carried().get(30).precision());
        assertEquals(strict, session.carried().get(31).precision());
    }

    @Test
    @DisplayName("when two anchors want the same square, the first to claim it keeps it")
    void conflictsResolveToOneAnchor() {
        // Deliberate rather than merged: two anchors stocking one square is a conflict the player
        // needs to see and sort out, and a mark blending both would be describing an arrangement
        // that does not exist.
        TransferPrecision strict = new TransferPrecision(true, true, true);

        GoggleSlots.Session session = GoggleSlots.collect(List.of(
                anchor(anchorFor(CHEST), TransferPrecision.NONE, 30),
                nudgedOntoTheChest(strict, 30)), CHEST, MENU_SIZE);

        assertNotNull(session);
        assertEquals(1, session.carried().size());
        assertEquals(TransferPrecision.NONE, session.carried().get(30).precision());
    }
}
