package com.skilles.chronoclones.entity;

import java.util.UUID;

import com.skilles.chronoclones.registry.ModEntities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
//? if >=26 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ChronoCloneEntity extends Entity {

    private static final EntityDataAccessor<String> AUTHOR_ID =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> AUTHOR_NAME =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<ItemStack> HELD_ITEM =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<ItemStack> OFFHAND_ITEM =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.ITEM_STACK);

    private final WalkAnimationState walkAnimation = new WalkAnimationState();

    public ChronoCloneEntity(EntityType<? extends ChronoCloneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static ChronoCloneEntity create(Level level) {
        return new ChronoCloneEntity(ModEntities.CHRONO_GHOST.get(), level);
    }

    public void driveTo(Vec3 pos, float yaw, float pitch) {
        this.setPos(pos.x, pos.y, pos.z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setYHeadRot(yaw);
        this.setDeltaMovement(Vec3.ZERO);
    }

    public void setAuthor(UUID authorId, String authorName) {
        this.entityData.set(AUTHOR_ID, authorId.toString());
        this.entityData.set(AUTHOR_NAME, authorName);
    }

    public @Nullable UUID authorId() {
        String raw = this.entityData.get(AUTHOR_ID);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    public String authorName() {
        return this.entityData.get(AUTHOR_NAME);
    }

    public void setOffhandItem(ItemStack stack) {
        if (!ItemStack.matches(this.entityData.get(OFFHAND_ITEM), stack)) {
            this.entityData.set(OFFHAND_ITEM, stack.copy());
        }
    }

    public ItemStack offhandItem() {
        return this.entityData.get(OFFHAND_ITEM);
    }

    public void setHeldItem(ItemStack stack) {
        if (!ItemStack.matches(this.entityData.get(HELD_ITEM), stack)) {
            this.entityData.set(HELD_ITEM, stack.copy());
        }
    }

    public ItemStack heldItem() {
        return this.entityData.get(HELD_ITEM);
    }

    public WalkAnimationState walkAnimation() {
        return this.walkAnimation;
    }

    @Override
    public void tick() {
        float travelled = (float) Mth.length(this.getX() - this.xo, 0.0, this.getZ() - this.zo);
        //? if >=26 {
        this.walkAnimation.update(Math.min(travelled * 4.0f, 1.0f), 0.4f, 1.0f);
        //?} else {
        /*this.walkAnimation.update(Math.min(travelled * 4.0f, 1.0f), 0.4f);
        *///?}
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    //? if >=26 {
    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }
    //?} else {
    /*@Override
    public boolean canBeCollidedWith() {
        return false;
    }
    *///?}

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    //? if >=26 {
    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }
    //?} else {
    /*@Override
    public boolean hurt(@NonNull DamageSource source, float amount) {
        return false;
    }
    *///?}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    //? if >=1.20.5 {
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AUTHOR_ID, "");
        builder.define(AUTHOR_NAME, "");
        builder.define(HELD_ITEM, ItemStack.EMPTY);
        builder.define(OFFHAND_ITEM, ItemStack.EMPTY);
    }
    //?} else {
    /*@Override
    protected void defineSynchedData() {
        this.entityData.define(AUTHOR_ID, "");
        this.entityData.define(AUTHOR_NAME, "");
        this.entityData.define(HELD_ITEM, ItemStack.EMPTY);
        this.entityData.define(OFFHAND_ITEM, ItemStack.EMPTY);
    }
    *///?}

    //? if >=26 {
    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {}
    //?} else {
    /*@Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    *///?}
}
