package com.skilles.chronoclones.client;

import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.client.preview.PreviewCache;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which nearby routines mark up an open container.
 *
 * <p>The marks are drawn from anchors the player is not looking at and may not have noticed, so the
 * ways this goes wrong are quiet ones: a chest marked up by a routine that works the chest next
 * door, or a routine's squares missed because a second anchor got there first. Neither announces
 * itself — the highlight simply describes something that is not going to happen.
 *
 * <p>Carrier stacks are empty throughout, because a populated one cannot be built without a loaded
 * datapack. What is asserted here is which squares are claimed and by how many anchors; what is
 * drawn on them is a rendering question with no test that could reach it.
 */
class GoggleSlotsTest {

    private static final BlockPos CHEST = new BlockPos(10, 64, 10);
    private static final int MENU_SIZE = 27 + 36;

    private static PreviewCache.Target anchor(BlockPos pos, int... carrierSlots) {
        return anchor(pos, BlockPos.ZERO, carrierSlots);
    }

    /** An anchor at {@code pos}, north-facing, working the container one step north of its origin. */
    private static PreviewCache.Target anchor(BlockPos pos, BlockPos nudge, int... carrierSlots) {
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
                DiagnosticState.NONE, nudge);
    }

    /** The anchor position whose local (0, 0, -1) lands on the chest, for a north-facing anchor. */
    private static BlockPos anchorFor(BlockPos container) {
        return container.south();
    }

    @Test
    @DisplayName("a routine that works this container claims its squares")
    void matchingRoutineIsCollected() {
        GoggleSlots.Session session =
                GoggleSlots.collect(List.of(anchor(anchorFor(CHEST), 30)), CHEST, MENU_SIZE);

        assertNotNull(session);
        assertTrue(session.carried().containsKey(30), "carried: " + session.carried().keySet());
        assertTrue(session.touched().contains(3));
    }

    @Test
    @DisplayName("a routine working a different container is ignored")
    void otherContainersAreIgnored() {
        // Same routine, an anchor one block over — so its session lands on the chest next door.
        assertNull(GoggleSlots.collect(
                List.of(anchor(anchorFor(CHEST).east(), 30)), CHEST, MENU_SIZE));
    }

    @Test
    @DisplayName("a session recorded against a differently shaped menu is ignored")
    void differentMenuShapeIsIgnored() {
        // Slot indices only mean anything relative to the menu that produced them, so marking up a
        // menu of another shape would point at squares chosen by arithmetic rather than by anyone.
        assertNull(GoggleSlots.collect(
                List.of(anchor(anchorFor(CHEST), 30)), CHEST, 3 + 36));
    }

    @Test
    @DisplayName("two anchors sharing a container are both shown")
    void anchorsSharingAContainerAreMerged() {
        // A chest worked by two routines is an ordinary way to build, and showing one of them would
        // be showing the wrong half of what happens there. The second anchor has to be nudged onto
        // the chest — two of them cannot stand in the same block — so this covers the highlight
        // following the nudge as well as the anchor's own position.
        GoggleSlots.Session session = GoggleSlots.collect(List.of(
                anchor(anchorFor(CHEST), 30),
                anchor(anchorFor(CHEST).above(), new BlockPos(0, -1, 0), 31)), CHEST, MENU_SIZE);

        assertNotNull(session);
        assertTrue(session.carried().containsKey(30), "carried: " + session.carried().keySet());
        assertTrue(session.carried().containsKey(31), "carried: " + session.carried().keySet());
    }
}
