package com.skilles.chronoclones.replay;

import com.mojang.authlib.GameProfile;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ExperienceStore;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import com.skilles.chronoclones.platform.ClonePlayer;
import org.jspecify.annotations.Nullable;

/** A fake player per clone of one anchor, reset between actions. */
public final class AnchorFakePlayer {

    private final Map<Integer, ClonePlayer> players = new HashMap<>();

    private final BlockPos anchorPos;

    public AnchorFakePlayer(BlockPos anchorPos) {
        this.anchorPos = anchorPos;
    }

    public ClonePlayer acquire(ServerLevel level, Operator operator, int clone,
                              Vec3 position, float yaw, float pitch, InteractionHand hand,
                              ItemStack held) {
        ClonePlayer actor = playerIn(level, operator, clone);
        resetForAction(actor);

        // useItemOn branches on game mode: a spectator interacts with nothing, and a creative
        // player never consumes items.
        if (actor.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            actor.setGameMode(GameType.SURVIVAL);
        }

        actor.setPos(position.x, position.y, position.z);
        actor.setYRot(yaw);
        actor.setXRot(pitch);
        actor.setYHeadRot(yaw);

        // Vanilla divides digging speed by five off the ground, and a teleported player is
        // always technically falling.
        actor.setOnGround(true);

        hold(actor, hand, held.copy());

        setExperience(actor, operator.experience());

        return actor;
    }

    /** Rebuilt if the level differs: a player bound to the wrong one fires its events into it. */
    private ClonePlayer playerIn(ServerLevel level, Operator operator, int clone) {
        ClonePlayer existing = players.get(clone);
        if (existing != null && existing.level() == level) {
            return existing;
        }
        if (existing != null) {
            existing.discard();
        }
        ClonePlayer made = new ClonePlayer(level, new GameProfile(operator.id(), operator.name()));
        players.put(clone, made);
        return made;
    }

    /**
     * Takes back the held item and whatever experience the action left.
     *
     * <p>Read as a level and a fraction, not from {@code totalExperience}, which vanilla only ever
     * adds to: an anvil charging a level for its work was charging it to nobody.
     */
    public void release(Operator operator, ClonePlayer actor) {
        sweepOrbs(actor);
        operator.setExperience(
                ExperienceStore.pointsFor(actor.experienceLevel, actor.experienceProgress));
        resetAfterAction(actor);
    }

    /** For tests and diagnostics. Nothing in the mod's own behaviour reads this. */
    public @Nullable ClonePlayer current(int clone) {
        return players.get(clone);
    }

    public void discard() {
        for (ClonePlayer actor : players.values()) {
            spillHeld(actor);
            actor.discard();
        }
        players.clear();
    }

    private void spillHeld(ClonePlayer actor) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = actor.getItemInHand(hand);
            if (held.isEmpty()) {
                continue;
            }
            actor.setItemInHand(hand, ItemStack.EMPTY);
            Containers.dropItemStack(actor.level(), anchorPos.getX() + 0.5,
                    anchorPos.getY() + 1.0, anchorPos.getZ() + 0.5, held);
        }
    }

    /** A fake player never ticks, so its attack cooldown never refills on its own. */
    public static void chargeAttack(ClonePlayer actor) {
        actor.attackStrengthTicker = FULLY_CHARGED_TICKS;
    }

    private static final int FULLY_CHARGED_TICKS = 1000;

    private void resetForAction(ClonePlayer actor) {
        clearTransientState(actor);
    }

    private void resetAfterAction(ClonePlayer actor) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack lastHeld = actor.getItemInHand(hand);
            if (!lastHeld.isEmpty()) {
                //? if >=26 {
                actor.getCooldowns().removeCooldown(actor.getCooldowns().getCooldownGroup(lastHeld));
                //?} else {
                /*actor.getCooldowns().removeCooldown(lastHeld.getItem());
                *///?}
            }
            hold(actor, hand, ItemStack.EMPTY);
        }
        setExperience(actor, 0);
        clearTransientState(actor);
        spillLeftovers(actor);
    }

    private void clearTransientState(ClonePlayer actor) {
        actor.stopUsingItem();
        actor.removeAllEffects();
        actor.clearFire();
        actor.setRemainingFireTicks(0);
        actor.setTicksFrozen(0);
        actor.setHealth(actor.getMaxHealth());
        actor.setAbsorptionAmount(0.0f);
        actor.resetFallDistance();
        actor.setSprinting(false);
        actor.setShiftKeyDown(false);
        actor.setPose(Pose.STANDING);
        actor.attackStrengthTicker = 0;

        if (actor.containerMenu != actor.inventoryMenu) {
            actor.containerMenu = actor.inventoryMenu;
        }
        actor.containerMenu.setCarried(ItemStack.EMPTY);
    }

    /**
     * Anything left in the player's own slots, dropped rather than deleted.
     *
     * <p>A container session loads and drains these itself, so reaching here means one of them
     * failed partway. Clearing it silently would eat a routine's stock for no findable reason.
     */
    private void spillLeftovers(ClonePlayer actor) {
        Inventory inventory = actor.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack left = inventory.getItem(slot);
            if (left.isEmpty()) {
                continue;
            }
            inventory.setItem(slot, ItemStack.EMPTY);
            Chronoclones.LOGGER.warn("An action left {} in {}'s hands; dropping it at {}",
                    left, actor.getGameProfile().name(), anchorPos);
            Containers.dropItemStack(actor.level(), anchorPos.getX() + 0.5,
                    anchorPos.getY() + 1.0, anchorPos.getZ() + 0.5, left);
        }
    }

    private static final double ORB_REACH = 1.0;

    /** Vanilla collects orbs while ticking, which a fake player never does. */
    private static void sweepOrbs(ClonePlayer actor) {
        if (!(actor.level() instanceof ServerLevel level)) {
            return;
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class,
                actor.getBoundingBox().inflate(ORB_REACH))) {
            actor.giveExperiencePoints(orb.getValue());
            orb.discard();
        }
    }

    /** giveExperiencePoints only adds, and vanilla has no setter. */
    private static void setExperience(ClonePlayer actor, int points) {
        actor.experienceLevel = 0;
        actor.experienceProgress = 0.0f;
        actor.totalExperience = 0;
        if (points > 0) {
            actor.giveExperiencePoints(points);
        }
    }

    /** Vanilla applies equipment attribute modifiers while ticking, which this never does. */
    private static void hold(ClonePlayer actor, InteractionHand hand, ItemStack stack) {
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;

        ItemStack previous = actor.getItemInHand(hand);
        if (!previous.isEmpty()) {
            //? if >=1.20.5 {
            previous.forEachModifier(slot, (attribute, modifier) -> {
                AttributeInstance instance = actor.getAttributes().getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                }
            });
            //?} else {
            /*previous.getAttributeModifiers(slot).forEach((attribute, modifier) -> {
                AttributeInstance instance = actor.getAttributes().getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.getId());
                }
            });
            *///?}
        }

        actor.setItemInHand(hand, stack);

        if (!stack.isEmpty()) {
            //? if >=1.20.5 {
            stack.forEachModifier(slot, (attribute, modifier) -> {
                AttributeInstance instance = actor.getAttributes().getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                    instance.addTransientModifier(modifier);
                }
            });
            //?} else {
            /*stack.getAttributeModifiers(slot).forEach((attribute, modifier) -> {
                AttributeInstance instance = actor.getAttributes().getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.getId());
                    instance.addTransientModifier(modifier);
                }
            });
            *///?}
        }
    }
}
