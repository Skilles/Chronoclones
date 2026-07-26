package com.skilles.chronoclones.replay;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Acquires the shared fake player that performs every world mutation.
 *
 * <p><b>Attribution resolves from the anchor OWNER, never the recording author.</b> This is the
 * security-critical half of a recording can be authored by anyone and handed to anyone,
 * so if breaks were attributed to the author, a crafted shard would let you destroy blocks in
 * someone else's name — and pass their land claims. The author's identity is purely cosmetic and is
 * used only to decide whose skin the ghost wears.
 *
 * <p>{@link FakePlayerFactory} caches one instance per {@code (level, profile)} pair, so this is
 * one fake player per owner per level rather than one per clone. It is repositioned and re-equipped
 * immediately before each action.
 */
public final class AnchorFakePlayer {

    private AnchorFakePlayer() {}

    /**
     * The fake player acting for {@code ownerId}, positioned and equipped for one action.
     *
     * @param ownerId   anchor owner — the identity every event and permission check will see
     * @param ownerName anchor owner's name, for readable logs and protection-mod messages
     */
    public static FakePlayer acquire(ServerLevel level, UUID ownerId, String ownerName,
                                     Vec3 position, float yaw, float pitch, ItemStack held) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(ownerId, ownerName));

        // Pinned to survival, not left at whatever the server's default happens to be. Two of the
        // three branches in ServerPlayerGameMode.useItemOn key off the game mode: a spectator opens
        // menus and interacts with nothing, and a creative player's items are never consumed. An
        // clone that silently stopped charging for its materials on a creative-default server would
        // be a balance hole; one that quietly did nothing on a spectator-default server would be a
        // bug with no symptom.
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            player.setGameMode(GameType.SURVIVAL);
        }

        player.setPos(position.x, position.y, position.z);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);

        // Standing, as far as mining speed is concerned. Vanilla divides digging speed by five while
        // a player is off the ground, to stop people mining mid-jump — but a fake player teleported
        // to a block is always technically falling, so without this every clone mines at a fifth
        // speed for a reason that has nothing to do with what was recorded.
        player.setOnGround(true);

        // The recorded template is a copy; the fake player must never consume or damage it.
        player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());

        return player;
    }

    /** Clears the held item so a stale tool cannot influence an unrelated later action. */
    public static void release(FakePlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
}
