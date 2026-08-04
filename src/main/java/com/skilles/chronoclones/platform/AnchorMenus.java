package com.skilles.chronoclones.platform;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/**
 * The two menu operations the loaders disagree on: creating a menu type whose client side is
 * seeded with extra data, and opening it with that data written.
 */
public final class AnchorMenus {

    private AnchorMenus() {}

    public static MenuType<ChronoAnchorMenu> anchorMenuType() {
        return IMenuTypeExtension.create(ChronoAnchorMenu::new);
    }

    /** Opens the anchor screen with the block position and action timeline the client ctor reads. */
    public static void open(ServerPlayer player, ChronoAnchorBlockEntity anchor) {
        player.openMenu(anchor, (RegistryFriendlyByteBuf buffer) -> {
            buffer.writeBlockPos(anchor.getBlockPos());
            ChronoAnchorMenu.writeTimeline(buffer, anchor.getRecording());
        });
    }
}
