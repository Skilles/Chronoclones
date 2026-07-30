package com.skilles.chronoclones.replay;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Acquires the shared fake player that performs every world mutation.
 */
public final class AnchorFakePlayer {

    private AnchorFakePlayer() {}

    /**
     * The fake player acting for {@code operator}, positioned, equipped and funded for one action.
     */
    public static FakePlayer acquire(ServerLevel level, Operator operator,
                                     Vec3 position, float yaw, float pitch, ItemStack held) {
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(operator.id(), operator.name()));

        // Pinned to survival: ServerPlayerGameMode.useItemOn branches on game mode, and a
        // spectator interacts with nothing while a creative player's items are never consumed.
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            player.setGameMode(GameType.SURVIVAL);
        }

        player.setPos(position.x, position.y, position.z);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);

        // Vanilla divides digging speed by five while a player is off the ground, and a fake
        // player teleported to a block is always technically falling.
        player.setOnGround(true);

        // The recorded template is a copy; the fake player must never consume or damage it.
        hold(player, held.copy());

        // Lent, not given: whatever it earns or spends comes back on release.
        setExperience(player, operator.experience());

        return player;
    }

    /**
     * Takes back the held item and whatever experience the action left the player holding.
     */
    public static void release(Operator operator, FakePlayer player) {
        operator.setExperience(player.totalExperience);
        setExperience(player, 0);
        hold(player, ItemStack.EMPTY);
    }

    /**
     * Sets a total directly, which vanilla has no method for: {@code giveExperiencePoints} adds, and
     * the shared fake player must start each action with exactly what its clone banked.
     */
    private static void setExperience(FakePlayer player, int points) {
        player.experienceLevel = 0;
        player.experienceProgress = 0.0f;
        player.totalExperience = 0;
        if (points > 0) {
            player.giveExperiencePoints(points);
        }
    }

    /**
     * Equips a stack and moves its attribute modifiers with it.
     *
     * <p>Vanilla applies these while ticking equipment changes, which a fake player never does, so
     * without this a clone swinging a netherite sword hits for a bare hand's damage.
     */
    private static void hold(FakePlayer player, ItemStack stack) {
        ItemStack previous = player.getMainHandItem();
        if (!previous.isEmpty()) {
            previous.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                AttributeInstance instance = player.getAttributes().getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                }
            });
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        if (!stack.isEmpty()) {
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                AttributeInstance instance = player.getAttributes().getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                    instance.addTransientModifier(modifier);
                }
            });
        }
    }
}
