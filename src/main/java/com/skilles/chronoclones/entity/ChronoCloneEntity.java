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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Visual-only clone. Never a FakePlayer — this entity does not act on the world at all;
 * its owning {@code ChronoAnchorBlockEntity} does, through a shared fake player.
 *
 * <p>Position is written directly by the anchor every tick. This entity has no movement logic of
 * its own on purpose: the whole point of the Day 1 drift spike is that position is a pure
 * function of an integer playhead, never an accumulated delta.
 *
 * <p>It carries the recording's <b>author</b>, which is the one place that identity is used for
 * anything: whose skin the ghost wears. Authorship is cosmetic by design and the anchor's owner is
 * what every permission check resolves from — see {@code ChronoAnchorBlockEntity} for why those must
 * never be the same field.
 */
public class ChronoCloneEntity extends Entity {

    /**
     * Author identity, synced for rendering only.
     *
     * <p>A UUID string rather than a UUID: 26.2 has no {@code OPTIONAL_UUID} serialiser, and the
     * client needs the id as well as the name — the id is what picks the right default skin when the
     * session server has nothing, which is every single-player world and every offline-mode server.
     */
    private static final EntityDataAccessor<String> AUTHOR_ID =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> AUTHOR_NAME =
            SynchedEntityData.defineId(ChronoCloneEntity.class, EntityDataSerializers.STRING);

    /**
     * Drives the walk cycle.
     *
     * <p>Derived from how far the entity actually moved rather than synced, because the anchor
     * already sends position every tick and a second stream saying the same thing would be pure
     * waste. {@code Level} calls {@code setOldPosAndRot()} immediately before {@code tick()} on both
     * sides, so the previous position is available here without keeping any of our own.
     */
    private final WalkAnimationState walkAnimation = new WalkAnimationState();

    /**
     * Client-side skin lookup, resolved lazily by the renderer and cached for the entity's life.
     *
     * <p>Held here rather than in the renderer because renderers are singletons per entity type: a
     * map there would keep an entry for every author seen in the session. {@code PlayerSkin} is a
     * common class, so this stays server-safe even though only the client ever writes it.
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

    /** Set by the anchor. Bypasses movement entirely — no deltas, no physics. */
    public void driveTo(Vec3 pos, float yaw, float pitch) {
        this.setPos(pos.x, pos.y, pos.z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setYHeadRot(yaw);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** Called by the anchor when the ghost is spawned, once per routine. */
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
        // Deliberately does not call super.tick(): no gravity, no drag, no collision resolution.
        // The anchor is the only thing that moves this entity.
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
        builder.define(AUTHOR_ID, "");
        builder.define(AUTHOR_NAME, "");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
