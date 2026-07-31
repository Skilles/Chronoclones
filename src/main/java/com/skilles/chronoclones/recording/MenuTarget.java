package com.skilles.chronoclones.recording;

import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/** What a container session was opened on: a block, or a kind of entity. */
public sealed interface MenuTarget {

    Kind kind();

    BlockPos localBlock();

    Vec3 localPoint();

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

    record Block(BlockPos localPos, Optional<Holder<net.minecraft.world.level.block.Block>> expectedBlock)
            implements MenuTarget {
        public Block(BlockPos localPos) {
            this(localPos, Optional.empty());
        }

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
