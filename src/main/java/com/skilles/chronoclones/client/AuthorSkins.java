package com.skilles.chronoclones.client;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import com.skilles.chronoclones.network.SkinPayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/** The skins of the authors whose routines are running nearby, cached per author. */
public final class AuthorSkins {

    private AuthorSkins() {}

    private static final Map<UUID, Supplier<PlayerSkin>> KNOWN = new ConcurrentHashMap<>();

    private static final Set<UUID> ASKED = ConcurrentHashMap.newKeySet();

    public static PlayerSkin of(UUID author) {
        Supplier<PlayerSkin> known = KNOWN.get(author);
        if (known != null) {
            return known.get();
        }

        Supplier<PlayerSkin> online = fromPlayerList(author);
        if (online != null) {
            KNOWN.put(author, online);
            return online.get();
        }

        if (ASKED.add(author)) {
            ClientPacketDistributor.sendToServer(new SkinPayloads.Request(author));
        }
        return DefaultPlayerSkin.get(author);
    }

    private static @Nullable Supplier<PlayerSkin> fromPlayerList(UUID author) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return null;
        }
        PlayerInfo info = connection.getPlayerInfo(author);
        return info == null ? null : info::getSkin;
    }

    public static void accept(SkinPayloads.Reply reply) {
        reply.profile().ifPresentOrElse(
                profile -> KNOWN.put(reply.author(), lookupFor(profile)),
                () -> KNOWN.put(reply.author(), unresolved(reply.author())));
    }

    private static Supplier<PlayerSkin> lookupFor(GameProfile profile) {
        // Insecure: a routine's author is not this connection's player, so their textures
        // carry no signature this client could check.
        return Minecraft.getInstance().getSkinManager().createLookup(profile, false);
    }

    private static Supplier<PlayerSkin> unresolved(UUID author) {
        PlayerSkin fallback = DefaultPlayerSkin.get(author);
        return () -> fallback;
    }

    public static void forget() {
        KNOWN.clear();
        ASKED.clear();
    }
}
