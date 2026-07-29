package com.skilles.chronoclones.replay;

import java.util.UUID;

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
     * The fake player acting for {@code ownerId}, positioned and equipped for one action.
     *
     * @param ownerId   anchor owner: the identity every event and permission check will see
     * @param ownerName anchor owner's name, for readable logs and protection-mod messages
     */
    public static FakePlayer acquire(ServerLevel level, UUID ownerId, String ownerName,
                                     Vec3 position, float yaw, float pitch, ItemStack held) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(ownerId, ownerName));

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

        return player;
    }

    /** Clears the held item so a stale tool cannot influence an unrelated later action. */
    public static void release(FakePlayer player) {
        hold(player, ItemStack.EMPTY);
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
