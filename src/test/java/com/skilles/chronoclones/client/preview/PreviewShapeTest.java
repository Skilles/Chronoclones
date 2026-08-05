package com.skilles.chronoclones.client.preview;

import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
//? if >=26 {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewShapeTest {

    private static final BlockPos ANCHOR = new BlockPos(100, 64, 100);
    private static final UUID AUTHOR = UUID.fromString("0000dead-0000-0000-0000-00000000beef");

    private static Recording of(ChronoAction... actions) {
        List<TimedAction> timed = new java.util.ArrayList<>();
        for (int i = 0; i < actions.length; i++) {
            timed.add(new TimedAction(i + 1, actions[i]));
        }
        return new Recording(List.of(new MotionSample(0, Vec3.ZERO, 0f, 0f)),
                List.copyOf(timed), 20, "Author", AUTHOR);
    }

    private static ChronoAction.BreakBlock breakAt(int x, int y, int z) {
        return new ChronoAction.BreakBlock(new BlockPos(x, y, z),
                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE), ItemStack.EMPTY);
    }

    private static ChronoAction.AttackEntity attackAt(double x, double y, double z) {
        return new ChronoAction.AttackEntity(new Vec3(x, y, z),
                //? if >=26 {
                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW), ItemStack.EMPTY);
                //?} else {
                /*BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.COW), ItemStack.EMPTY);
                *///?}
    }

    @Test
    @DisplayName("an attack-only routine previews as a reach volume, not as nothing")
    void attackIsVisible() {
        PreviewShape shape = PreviewShape.of(of(attackAt(0, 0, -2)), ANCHOR, Direction.NORTH);

        assertFalse(shape.isEmpty(), "an attacking routine that previews as nothing cannot be inspected");
        assertEquals(1, shape.volumes().size());
        assertEquals(PreviewShape.Kind.ATTACK, shape.volumes().getFirst().kind());
        assertTrue(shape.volumes().getFirst().radius() > 0.0);
        assertEquals(0, shape.marks().size(), "an attack must not claim a specific block");
    }

    @Test
    @DisplayName("a mob interaction gets its own, larger volume")
    void interactIsVisible() {
        PreviewShape shape = PreviewShape.of(
                of(new ChronoAction.InteractEntity(new Vec3(0, 0, -2),
                        //? if >=26 {
                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW),
                        //?} else {
                        /*BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.COW),
                        *///?}
                        InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(Items.BUCKET))),
                ANCHOR, Direction.NORTH);

        assertEquals(1, shape.volumes().size());
        assertEquals(PreviewShape.Kind.INTERACT, shape.volumes().getFirst().kind());
    }

    @Test
    @DisplayName("a session on a villager is drawn where it stands, not at the square it rounds to")
    void entitySessionIsAVolume() {
        Vec3 standing = new Vec3(0.5, 0.0, -2.5);
        PreviewShape shape = PreviewShape.of(
                of(new ChronoAction.UseContainer(
                        new MenuTarget.Entity(standing,
                                //? if >=26 {
                                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.VILLAGER)),
                                //?} else {
                                /*BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.VILLAGER)),
                                *///?}
                        39, List.of(), List.of())),
                ANCHOR, Direction.NORTH);

        assertEquals(0, shape.marks().size(), "a villager was drawn as a block");
        assertEquals(1, shape.volumes().size());
        assertEquals(new Vec3(100.5, 64.0, 97.5), shape.volumes().getFirst().centre());
    }

    @Test
    @DisplayName("a session on a block is still a block")
    void blockSessionIsAMark() {
        PreviewShape shape = PreviewShape.of(
                of(new ChronoAction.UseContainer(
                        new MenuTarget.Block(new BlockPos(0, 0, -2)), 63, List.of(), List.of())),
                ANCHOR, Direction.NORTH);

        assertEquals(1, shape.marks().size());
        assertEquals(0, shape.volumes().size());
        assertEquals(PreviewShape.Kind.TRANSFER, shape.marks().getFirst().kind());
    }

    @Test
    @DisplayName("the volume sits where the action was recorded, rotated with the anchor")
    void volumeFollowsTheAnchorFacing() {
        Vec3 north = PreviewShape.of(of(attackAt(0, 0, -2)), ANCHOR, Direction.NORTH)
                .volumes().getFirst().centre();
        Vec3 east = PreviewShape.of(of(attackAt(0, 0, -2)), ANCHOR, Direction.EAST)
                .volumes().getFirst().centre();

        assertEquals(new Vec3(100, 64, 98), north);
        assertEquals(new Vec3(102, 64, 100), east);
    }

    @Test
    @DisplayName("only the failing step is flagged")
    void failureMarksOneStep() {
        PreviewShape shape = PreviewShape.of(
                of(breakAt(0, 0, -1), breakAt(0, 0, -2), breakAt(0, 0, -3)),
                ANCHOR, Direction.NORTH, new BlockPos(0, 0, -2));

        assertEquals(3, shape.marks().size());
        assertFalse(shape.marks().get(0).failing());
        assertTrue(shape.marks().get(1).failing());
        assertFalse(shape.marks().get(2).failing());
    }

    @Test
    @DisplayName("with nothing failing, no step is flagged")
    void noFailureFlagsNothing() {
        PreviewShape shape = PreviewShape.of(of(breakAt(0, 0, -1)), ANCHOR, Direction.NORTH);

        assertFalse(shape.marks().getFirst().failing(),
                "a healthy routine drawn in alarm colours teaches players to ignore them");
    }
}
