package com.skilles.chronoclones.recording;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuTargetTest {

    private static final BlockPos ORIGIN = new BlockPos(100, 64, 100);

    private static MenuTarget entity(Vec3 at) {
        return new MenuTarget.Entity(at, BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.VILLAGER));
    }

    @Test
    @DisplayName("a block target is the centre of the square the anchor's facing puts it in")
    void blockTargetCentresAfterRotating() {
        MenuTarget target = new MenuTarget.Block(new BlockPos(0, 0, -3));

        assertEquals(Vec3.atCenterOf(new BlockPos(100, 64, 97)),
                target.toWorld(ORIGIN, Direction.NORTH));

        Vec3 east = target.toWorld(ORIGIN, Direction.EAST);
        assertEquals(Vec3.atCenterOf(BlockPos.containing(east)), east,
                "a rotated block target no longer sits in the middle of a square: " + east);
        assertEquals(new BlockPos(103, 64, 100), BlockPos.containing(east));
    }

    @Test
    @DisplayName("every facing puts a block target in the middle of some square")
    void everyFacingKeepsTheBlockCentred() {
        MenuTarget target = new MenuTarget.Block(new BlockPos(2, 1, -3));

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Vec3 world = target.toWorld(ORIGIN, facing);
            assertEquals(Vec3.atCenterOf(BlockPos.containing(world)), world,
                    "facing " + facing + " put the target at " + world);
        }
    }

    @Test
    @DisplayName("an entity target keeps the exact point it was standing on")
    void entityTargetIsNotSnapped() {
        MenuTarget target = entity(new Vec3(0.25, 0.0, -3.5));

        assertEquals(new Vec3(100.25, 64.0, 96.5), target.toWorld(ORIGIN, Direction.NORTH));
    }

    @Test
    @DisplayName("the square a target reports is the square it is really in")
    void localBlockAgreesWithThePoint() {
        assertEquals(new BlockPos(0, 0, -4), entity(new Vec3(0.25, 0.0, -3.5)).localBlock());
        assertEquals(new BlockPos(0, 0, -3), new MenuTarget.Block(new BlockPos(0, 0, -3)).localBlock());
    }
}
