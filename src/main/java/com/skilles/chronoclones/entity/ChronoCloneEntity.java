package com.skilles.chronoclones.entity;

import com.skilles.chronoclones.registry.ModEntities;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Visual-only clone. Never a FakePlayer — this entity does not act on the world at all;
 * its owning {@code ChronoAnchorBlockEntity} does, through a shared fake player.
 *
 * <p>Position is written directly by the anchor every tick. This entity has no movement logic of
 * its own on purpose: the whole point of the Day 1 drift spike is that position is a pure
 * function of an integer playhead, never an accumulated delta.
 */
public class ChronoCloneEntity extends Entity {

    public ChronoCloneEntity(EntityType<? extends ChronoCloneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static ChronoCloneEntity create(Level level) {
        return new ChronoCloneEntity(ModEntities.CHRONO_GHOST.get(), level);
    }

    /** Set by the anchor. Bypasses movement entirely — no deltas, no physics. */
    public void driveTo(Vec3 pos, float yaw, float pitch) {
        this.setPos(pos.x, pos.y, pos.z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setYHeadRot(yaw);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        // Deliberately does not call super.tick(): no gravity, no drag, no collision resolution.
        // The anchor is the only thing that moves this entity.
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    /** Spec /Q4: ghosts are never interactable and never take damage. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synced data yet. The author's skin identity lands here on Day 10.
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
