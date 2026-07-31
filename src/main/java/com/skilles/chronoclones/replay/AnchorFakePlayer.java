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
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

/**
 * The player one anchor acts as, and the contract for handing it between actions.
 *
 * <p>One per anchor, not one per profile. It used to be the latter -- the shared instance
 * {@code FakePlayerFactory} hands out for a game profile -- which meant two anchors owned by the
 * same player were the same object, and anything one of them left behind was waiting for the other.
 * Only the held item and the experience were ever put back, so an item that set a cooldown, applied
 * an effect, set something alight or started a use carried all of it across.
 *
 * <p>One per clone within that anchor, too. An action can span ticks -- an item held down needs the
 * player that started holding it to still be the one holding it when it is let go -- and an anchor
 * with a splitter in it runs several clones at once, so a second clone reaching for the anchor's
 * player mid-draw would reset the first one's.
 *
 * <p><b>What a reset covers</b>, in both directions: held item and its attribute modifiers, banked
 * experience, any item being used, the cooldown of whatever was just used, status effects, fire and
 * freezing, health and absorption, fall distance, pose, sprinting and crouching, the attack
 * cooldown, and any menu left open. Anything a mod adds outside that list is a known gap -- the
 * player is per-anchor, so such a thing can still reach the next action of the <em>same</em> anchor,
 * but never another anchor's.
 */
public final class AnchorFakePlayer {

    private final Map<Integer, FakePlayer> players = new HashMap<>();

    /** Where to put anything an action abandoned, rather than voiding it. */
    private final BlockPos anchorPos;

    public AnchorFakePlayer(BlockPos anchorPos) {
        this.anchorPos = anchorPos;
    }

    /**
     * The player acting for {@code operator}, reset, positioned, equipped and funded for one action.
     */
    public FakePlayer acquire(ServerLevel level, Operator operator, int clone,
                              Vec3 position, float yaw, float pitch, ItemStack held) {
        FakePlayer actor = playerIn(level, operator, clone);
        resetForAction(actor);

        // Pinned to survival: ServerPlayerGameMode.useItemOn branches on game mode, and a
        // spectator interacts with nothing while a creative player's items are never consumed.
        if (actor.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            actor.setGameMode(GameType.SURVIVAL);
        }

        actor.setPos(position.x, position.y, position.z);
        actor.setYRot(yaw);
        actor.setXRot(pitch);
        actor.setYHeadRot(yaw);

        // Vanilla divides digging speed by five while a player is off the ground, and a fake
        // player teleported to a block is always technically falling.
        actor.setOnGround(true);

        // The recorded template is a copy; the fake player must never consume or damage it.
        hold(actor, held.copy());

        // Lent, not given: whatever it earns or spends comes back on release.
        setExperience(actor, operator.experience());

        return actor;
    }

    /**
     * The instance for this anchor, made on first use.
     *
     * <p>Rebuilt if the level ever differs, which a block entity's should not, but a player bound to
     * the wrong level would fire its events into it.
     */
    private FakePlayer playerIn(ServerLevel level, Operator operator, int clone) {
        FakePlayer existing = players.get(clone);
        if (existing != null && existing.level() == level) {
            return existing;
        }
        if (existing != null) {
            existing.discard();
        }
        FakePlayer made = new FakePlayer(level, new GameProfile(operator.id(), operator.name()));
        players.put(clone, made);
        return made;
    }

    /**
     * Takes back the held item and whatever experience the action left the player holding.
     *
     * <p>Read as a level and a fraction rather than from {@code totalExperience}, which vanilla only
     * ever adds to: {@code giveExperienceLevels(-1)} lowers the bar without lowering the total, so an
     * anvil charging a level for its work was charging it to nobody.
     */
    public void release(Operator operator, FakePlayer actor) {
        sweepOrbs(actor);
        operator.setExperience(
                ExperienceStore.pointsFor(actor.experienceLevel, actor.experienceProgress));
        resetAfterAction(actor);
    }

    /**
     * The player this anchor has made, or null if it has not acted yet.
     *
     * <p>For tests and diagnostics: the reset contract is the kind of thing that is easy to write,
     * easy to believe, and impossible to check from the outside without being able to look at what
     * it was supposed to have cleared. Nothing in the mod's own behaviour reads this.
     */
    public @Nullable FakePlayer current(int clone) {
        return players.get(clone);
    }

    /** Lets go of the player when the anchor does, so an unloaded anchor leaves nothing behind. */
    public void discard() {
        for (FakePlayer actor : players.values()) {
            // Anything still in its hand belongs to the anchor, not to a player about to vanish.
            spillHeld(actor);
            actor.discard();
        }
        players.clear();
    }

    /** Puts a loaned item on the ground rather than losing it with the player holding it. */
    private void spillHeld(FakePlayer actor) {
        ItemStack held = actor.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Containers.dropItemStack(actor.level(), anchorPos.getX() + 0.5,
                anchorPos.getY() + 1.0, anchorPos.getZ() + 0.5, held);
    }

    /**
     * Fills the attack cooldown, so the next swing lands at full strength.
     *
     * <p>Vanilla refills this by one every tick a player is alive, and scales damage, enchantment
     * bonuses, criticals and sweeping by how full it is. A fake player is never ticked, so the
     * counter it reads sits wherever the last swing reset it to -- which is zero, the uncharged
     * 0.2x. Set well past the slowest weapon's delay, because {@code getAttackStrengthScale} clamps.
     */
    public static void chargeAttack(FakePlayer actor) {
        actor.attackStrengthTicker = FULLY_CHARGED_TICKS;
    }

    /** Longer than any weapon's swing delay, which is what the scale clamps against. */
    private static final int FULLY_CHARGED_TICKS = 1000;

    // ------------------------------------------------------------------ the contract

    /** Everything the previous action may have left set, cleared before this one starts. */
    private void resetForAction(FakePlayer actor) {
        clearTransientState(actor);
    }

    /** The same, on the way out, so nothing is left set while the player sits between actions. */
    private void resetAfterAction(FakePlayer actor) {
        ItemStack lastHeld = actor.getMainHandItem();
        if (!lastHeld.isEmpty()) {
            // The cooldown this action just started. ItemCooldowns expires entries against a tick
            // counter that only advances while a player ticks, and this one never does, so a
            // cooldown set here would otherwise last until the anchor unloaded.
            actor.getCooldowns().removeCooldown(actor.getCooldowns().getCooldownGroup(lastHeld));
        }
        hold(actor, ItemStack.EMPTY);
        setExperience(actor, 0);
        clearTransientState(actor);
        spillLeftovers(actor);
    }

    /**
     * The state a single action can set that has nothing to do with the next one.
     */
    private void clearTransientState(FakePlayer actor) {
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

        // A container session closes its own menu; this catches the one that threw on the way.
        if (actor.containerMenu != actor.inventoryMenu) {
            actor.containerMenu = actor.inventoryMenu;
        }
        actor.containerMenu.setCarried(ItemStack.EMPTY);
    }

    /**
     * Anything still in the player's own squares, put on the ground rather than deleted.
     *
     * <p>A container session loads and drains this itself, so reaching anything here means one of
     * them failed partway. Dropping it is noisy and visible; clearing it silently would be a
     * routine that eats its owner's stock every so often for no reason anybody could find.
     */
    private void spillLeftovers(FakePlayer actor) {
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

    /** About an arm's length: what the clone is standing in, not what is lying across the room. */
    private static final double ORB_REACH = 1.0;

    /**
     * Picks up the experience orbs the clone is standing in, as a player walking through would.
     *
     * <p>Some sources hand experience over and some drop it at your feet: a furnace pops orbs rather
     * than awarding them. A fake player never ticks, so without this nothing would ever collect them
     * and a smelting routine would bury its own floor in orbs it earned.
     */
    private static void sweepOrbs(FakePlayer actor) {
        if (!(actor.level() instanceof ServerLevel level)) {
            return;
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class,
                actor.getBoundingBox().inflate(ORB_REACH))) {
            actor.giveExperiencePoints(orb.getValue());
            orb.discard();
        }
    }

    /**
     * Sets a total directly, which vanilla has no method for: {@code giveExperiencePoints} adds, and
     * the player must start each action with exactly what its clone banked.
     */
    private static void setExperience(FakePlayer actor, int points) {
        actor.experienceLevel = 0;
        actor.experienceProgress = 0.0f;
        actor.totalExperience = 0;
        if (points > 0) {
            actor.giveExperiencePoints(points);
        }
    }

    /**
     * Equips a stack and moves its attribute modifiers with it.
     *
     * <p>Vanilla applies these while ticking equipment changes, which a fake player never does, so
     * without this a clone swinging a netherite sword hits for a bare hand's damage.
     */
    private static void hold(FakePlayer actor, ItemStack stack) {
        ItemStack previous = actor.getMainHandItem();
        if (!previous.isEmpty()) {
            previous.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                AttributeInstance instance = actor.getAttributes().getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                }
            });
        }

        actor.setItemInHand(InteractionHand.MAIN_HAND, stack);

        if (!stack.isEmpty()) {
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                AttributeInstance instance = actor.getAttributes().getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                    instance.addTransientModifier(modifier);
                }
            });
        }
    }
}
