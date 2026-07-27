package com.skilles.chronoclones.entity;

import java.util.UUID;
import java.util.function.Supplier;

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
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Visual-only clone. Never a FakePlayer: the anchor acts, through a shared one. */
public class ChronoCloneEntity extends Entity {

    /**
     * Author identity, synced for rendering only.
     */
    private static final EntityDataAccessor<String> AUTHOR_ID =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> AUTHOR_NAME =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.STRING);

    /** What the clone appears to be holding. Cosmetic: see {@code ChronoAction.heldTemplate}. */
    private static final EntityDataAccessor<ItemStack> HELD_ITEM =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.ITEM_STACK);

    /**
     * Drives the walk cycle.
     */
    private final WalkAnimationState walkAnimation = new WalkAnimationState();

    /**
     * Client-side skin lookup, resolved lazily by the renderer and cached for the entity's life.
     */
    private @Nullable Supplier<PlayerSkin> skinLookup;
    private @Nullable UUID skinLookupFor;

    public ChronoCloneEntity(EntityType<? extends ChronoCloneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static ChronoCloneEntity create(Level level) {
        return new ChronoCloneEntity(ModEntities.CHRONO_GHOST.get(), level);
    }

    /** Set by the anchor. Bypasses movement entirely: no deltas, no physics. */
    public void driveTo(Vec3 pos, float yaw, float pitch) {
        this.setPos(pos.x, pos.y, pos.z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setYHeadRot(yaw);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** Called by the anchor when the clone is spawned, once per routine. */
    public void setAuthor(UUID authorId, String authorName) {
        this.entityData.set(AUTHOR_ID, authorId.toString());
        this.entityData.set(AUTHOR_NAME, authorName);
    }

    /** The author's id, or null before the anchor has assigned one. */
    public @Nullable UUID authorId() {
        String raw = this.entityData.get(AUTHOR_ID);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            // Synced strings are attacker-reachable in principle; a bad one costs a default skin.
            return null;
        }
    }

    public String authorName() {
        return this.entityData.get(AUTHOR_NAME);
    }

    /**
     * Sets the visibly held item, skipping the write when nothing changed.
     */
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

    /** The cached lookup, or null if none has been made for {@code author} yet. */
    public @Nullable Supplier<PlayerSkin> skinLookup(UUID author) {
        return author.equals(this.skinLookupFor) ? this.skinLookup : null;
    }

    public void setSkinLookup(UUID author, Supplier<PlayerSkin> lookup) {
        this.skinLookupFor = author;
        this.skinLookup = lookup;
    }

    @Override
    public void tick() {
        // No super.tick(): the anchor moves this entity, with no physics of its own.
        float travelled = (float) Mth.length(this.getX() - this.xo, 0.0, this.getZ() - this.zo);
        this.walkAnimation.update(Math.min(travelled * 4.0f, 1.0f), 0.4f, 1.0f);
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

    /** Clones are never interactable and never take damage. */
    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AUTHOR_ID, "");
        builder.define(AUTHOR_NAME, "");
        builder.define(HELD_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {}
}
