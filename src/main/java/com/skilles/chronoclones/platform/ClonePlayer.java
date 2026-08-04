package com.skilles.chronoclones.platform;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayer;
//?}

/**
 * The player a clone acts as. The superclass is the loader's fake-player foundation: NeoForge's
 * {@code FakePlayer} here, so NeoForge internals that special-case fake players keep doing so;
 * a Fabric build substitutes its own no-network {@code ServerPlayer} subclass.
 */
public class ClonePlayer extends /*? if neoforge {*/ FakePlayer /*?}*/ {

    public ClonePlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    /** Whether {@code player} is any mod's stand-in rather than a person. */
    public static boolean isFake(Player player) {
        //? if neoforge {
        return player.isFakePlayer();
        //?}
    }
}
