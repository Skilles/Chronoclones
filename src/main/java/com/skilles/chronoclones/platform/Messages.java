package com.skilles.chronoclones.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Small chat-surface calls whose vanilla names drifted across versions. */
public final class Messages {

    private Messages() {}

    /** The hotbar overlay line. */
    public static void overlay(Player player, Component message) {
        //? if >=26 {
        player.sendOverlayMessage(message);
        //?} else {
        /*player.displayClientMessage(message, true);*/
        //?}
    }
}
