package com.skilles.chronoclones.platform;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
//? if neoforge {
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
//?}

/**
 * The two menu operations the loaders disagree on: creating a menu type whose client side is
 * seeded with extra data, and opening it with that data written.
 */
public final class AnchorMenus {

    private AnchorMenus() {}

    //? if neoforge {
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
    //?} else {
    //? if >=1.20.5 {
    /*// The extra data travels as raw bytes so the menu keeps one buffer-reading ctor on both loaders.
    public static MenuType<ChronoAnchorMenu> anchorMenuType() {
        return new net.fabricmc.fabric.api.menu.v1.ExtendedMenuType<ChronoAnchorMenu, byte[]>(
                (syncId, inventory, data) -> new ChronoAnchorMenu(syncId, inventory,
                        new RegistryFriendlyByteBuf(
                                io.netty.buffer.Unpooled.wrappedBuffer(data),
                                inventory.player.registryAccess())),
                net.minecraft.network.codec.ByteBufCodecs.BYTE_ARRAY);
    }

    public static void open(ServerPlayer player, ChronoAnchorBlockEntity anchor) {
        player.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<byte[]>() {
            @Override
            public byte[] getScreenOpeningData(ServerPlayer receiver) {
                RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(), receiver.registryAccess());
                buffer.writeBlockPos(anchor.getBlockPos());
                ChronoAnchorMenu.writeTimeline(buffer, anchor.getRecording());
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return bytes;
            }

            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return anchor.getDisplayName();
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int syncId, net.minecraft.world.entity.player.Inventory inventory,
                    net.minecraft.world.entity.player.Player opener) {
                return anchor.createMenu(syncId, inventory, opener);
            }
        });
    }
    *///?} else {
    /*public static MenuType<ChronoAnchorMenu> anchorMenuType() {
        return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                (syncId, inventory, buf) -> new ChronoAnchorMenu(syncId, inventory,
                        new RegistryFriendlyByteBuf(buf, inventory.player.level().registryAccess())));
    }

    public static void open(ServerPlayer player, ChronoAnchorBlockEntity anchor) {
        player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer receiver,
                                               net.minecraft.network.FriendlyByteBuf buffer) {
                RegistryFriendlyByteBuf wrapped =
                        new RegistryFriendlyByteBuf(buffer, receiver.level().registryAccess());
                wrapped.writeBlockPos(anchor.getBlockPos());
                ChronoAnchorMenu.writeTimeline(wrapped, anchor.getRecording());
            }

            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return anchor.getDisplayName();
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int syncId, net.minecraft.world.entity.player.Inventory inventory,
                    net.minecraft.world.entity.player.Player opener) {
                return anchor.createMenu(syncId, inventory, opener);
            }
        });
    }
    *///?}
    //?}
}
