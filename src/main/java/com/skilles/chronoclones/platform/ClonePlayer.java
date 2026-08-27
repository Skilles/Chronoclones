package com.skilles.chronoclones.platform;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayer;
//?}

/**
 * The player a clone acts as. The superclass is the loader's fake-player foundation — NeoForge's
 * or Fabric API's {@code FakePlayer} — so loader internals that special-case fake players keep
 * doing so.
 */
public class ClonePlayer extends
        //? if neoforge {
        FakePlayer
        //?} else {
        //? if fabric {
        /*net.fabricmc.fabric.api.entity.FakePlayer
        *///?} else {
        /*net.minecraftforge.common.util.FakePlayer
        *///?}
        //?}
{

    public ClonePlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
        //? if !neoforge {
        /*// The NeoForge superclass already does this; the others leave it to the subclass.
        this.setInvulnerable(true);
        *///?}
    }

    /** Whether {@code player} is any mod's stand-in rather than a person. */
    public static boolean isFake(Player player) {
        //? if neoforge {
        return player.isFakePlayer();
        //?} else {
        //? if fabric {
        /*return player instanceof net.fabricmc.fabric.api.entity.FakePlayer;
        *///?} else {
        /*return player instanceof net.minecraftforge.common.util.FakePlayer;
        *///?}
        //?}
    }
}
