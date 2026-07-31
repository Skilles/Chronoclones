package com.skilles.chronoclones.client;

import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.client.preview.PreviewCache;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;
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

class GoggleSlotsTest {

    private static final BlockPos CHEST = new BlockPos(10, 64, 10);
    private static final int MENU_SIZE = 27 + 36;

    private static PreviewCache.Target anchor(BlockPos pos, int... carrierSlots) {
        return anchor(pos, BlockPos.ZERO, carrierSlots);
    }

    private static PreviewCache.Target anchor(BlockPos pos, BlockPos nudge, int... carrierSlots) {
        List<ChronoAction.UseContainer.CarrierSlot> carrier =
                java.util.Arrays.stream(carrierSlots)
                        .mapToObj(slot -> new ChronoAction.UseContainer.CarrierSlot(slot, ItemStack.EMPTY))
                        .toList();

        ChronoAction session = new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), MENU_SIZE, carrier,
                List.of(new SessionStep.RawClick(3, 0, ContainerInput.PICKUP)));

        Recording recording = new Recording(
                List.of(new MotionSample(0, Vec3.ZERO, 0f, 0f)),
                List.of(new TimedAction(1, session)),
                20, "Author", UUID.randomUUID());

        return new PreviewCache.Target(pos, Direction.NORTH, recording, false,
                DiagnosticState.NONE, nudge);
    }

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
        assertNull(GoggleSlots.collect(
                List.of(anchor(anchorFor(CHEST).east(), 30)), CHEST, MENU_SIZE));
    }

    @Test
    @DisplayName("a session recorded against a differently shaped menu is ignored")
    void differentMenuShapeIsIgnored() {
        assertNull(GoggleSlots.collect(
                List.of(anchor(anchorFor(CHEST), 30)), CHEST, 3 + 36));
    }

    @Test
    @DisplayName("two anchors sharing a container are both shown")
    void anchorsSharingAContainerAreMerged() {
        GoggleSlots.Session session = GoggleSlots.collect(List.of(
                anchor(anchorFor(CHEST), 30),
                anchor(anchorFor(CHEST).above(), new BlockPos(0, -1, 0), 31)), CHEST, MENU_SIZE);

        assertNotNull(session);
        assertTrue(session.carried().containsKey(30), "carried: " + session.carried().keySet());
        assertTrue(session.carried().containsKey(31), "carried: " + session.carried().keySet());
    }
}
