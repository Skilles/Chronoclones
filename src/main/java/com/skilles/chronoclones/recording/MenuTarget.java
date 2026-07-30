package com.skilles.chronoclones.recording;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * What a container session had open.
 *
 * <p>A block stays where it was put; a villager wanders. So an entity target is a point to look
 * around rather than a square to reach into, and the looking is the same {@code TargetRule} an attack
 * uses.
 */
public sealed interface MenuTarget {

    Kind kind();

    /** Where it was, as a square, for diagnostics and the anchor's radius check. */
    BlockPos localBlock();

    /** Where it was, exactly, for measuring a routine's reach. */
    Vec3 localPoint();

    /**
     * Where it is in the world, for an anchor at {@code origin} facing {@code facing}.
     *
     * <p>Here rather than at the call site because the two kinds do not rotate alike: a square
     * rotates as a square and is centred afterwards, where centring a square first and then rotating
     * it lands a block away from where it belongs.
     */
    Vec3 toWorld(BlockPos origin, Direction facing);

    enum Kind implements StringRepresentable {
        BLOCK("block"),
        ENTITY("entity");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public @NonNull String getSerializedName() {
            return name;
        }
    }

    record Block(BlockPos localPos) implements MenuTarget {

        @Override
        public Kind kind() {
            return Kind.BLOCK;
        }

        @Override
        public BlockPos localBlock() {
            return localPos;
        }

        @Override
        public Vec3 localPoint() {
            return Vec3.atCenterOf(localPos);
        }

        @Override
        public Vec3 toWorld(BlockPos origin, Direction facing) {
            return Vec3.atCenterOf(LocalSpace.toWorld(localPos, origin, facing));
        }
    }

    /** {@code expectedType} is a hint used to pick the best target, as an attack's is. */
    record Entity(Vec3 localPos, Holder<EntityType<?>> expectedType) implements MenuTarget {

        @Override
        public Kind kind() {
            return Kind.ENTITY;
        }

        @Override
        public BlockPos localBlock() {
            return BlockPos.containing(localPos);
        }

        @Override
        public Vec3 localPoint() {
            return localPos;
        }

        @Override
        public Vec3 toWorld(BlockPos origin, Direction facing) {
            return LocalSpace.toWorld(localPos, origin, facing);
        }
    }
}
